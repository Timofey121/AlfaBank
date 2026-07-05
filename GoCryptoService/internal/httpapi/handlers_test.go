package httpapi

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func doJSON(t *testing.T, h http.Handler, method, path string, body any) *httptest.ResponseRecorder {
	t.Helper()
	var buf bytes.Buffer
	if body != nil {
		if err := json.NewEncoder(&buf).Encode(body); err != nil {
			t.Fatalf("failed to encode request body: %v", err)
		}
	}
	req := httptest.NewRequest(method, path, &buf)
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func decodeBody(t *testing.T, rec *httptest.ResponseRecorder, dst any) {
	t.Helper()
	if err := json.Unmarshal(rec.Body.Bytes(), dst); err != nil {
		t.Fatalf("failed to decode response body %q: %v", rec.Body.String(), err)
	}
}

func generateKeystore(t *testing.T, h http.Handler, alias string) generateKeystoreResponse {
	t.Helper()
	rec := doJSON(t, h, http.MethodPost, "/api/v1/admin/generate-keystore", generateKeystoreRequest{
		Alias: alias, CN: "test.example.com", ValidityDays: 365,
	})
	if rec.Code != http.StatusCreated {
		t.Fatalf("generate-keystore: status = %d, body = %s", rec.Code, rec.Body.String())
	}
	var resp generateKeystoreResponse
	decodeBody(t, rec, &resp)
	return resp
}

func TestGenerateKeystore(t *testing.T) {
	h := newTestServer(t)
	resp := generateKeystore(t, h, "alias-1")
	if resp.Alias != "alias-1" {
		t.Errorf("Alias = %q, want alias-1", resp.Alias)
	}
	if resp.CertBase64 == "" {
		t.Error("expected non-empty certBase64")
	}
}

func TestSignAndVerify(t *testing.T) {
	h := newTestServer(t)
	generateKeystore(t, h, "signer")

	data := base64.StdEncoding.EncodeToString([]byte("hello world"))
	signRec := doJSON(t, h, http.MethodPost, "/api/v1/crypto/sign", signRequest{
		Data: data, KeyAlias: "signer", Mode: "ATTACHED",
	})
	if signRec.Code != http.StatusOK {
		t.Fatalf("sign: status = %d, body = %s", signRec.Code, signRec.Body.String())
	}
	var signResp signResponse
	decodeBody(t, signRec, &signResp)
	if signResp.Signature == "" {
		t.Fatal("expected non-empty signature")
	}

	verifyRec := doJSON(t, h, http.MethodPost, "/api/v1/crypto/verify", verifyRequest{
		Signature: signResp.Signature, Mode: "ATTACHED",
	})
	if verifyRec.Code != http.StatusOK {
		t.Fatalf("verify: status = %d, body = %s", verifyRec.Code, verifyRec.Body.String())
	}
	var verifyResp verifyResponse
	decodeBody(t, verifyRec, &verifyResp)
	if !verifyResp.Valid {
		t.Error("expected valid=true")
	}
}

func TestSignVerifyDetached(t *testing.T) {
	h := newTestServer(t)
	generateKeystore(t, h, "signer-detached")
	data := base64.StdEncoding.EncodeToString([]byte("detached payload"))

	signRec := doJSON(t, h, http.MethodPost, "/api/v1/crypto/sign", signRequest{
		Data: data, KeyAlias: "signer-detached", Mode: "DETACHED",
	})
	var signResp signResponse
	decodeBody(t, signRec, &signResp)

	verifyRec := doJSON(t, h, http.MethodPost, "/api/v1/crypto/verify", verifyRequest{
		Signature: signResp.Signature, Data: data, Mode: "DETACHED",
	})
	var verifyResp verifyResponse
	decodeBody(t, verifyRec, &verifyResp)
	if !verifyResp.Valid {
		t.Error("expected valid=true for detached signature")
	}
}

func TestEncryptDecryptRoundTrip(t *testing.T) {
	h := newTestServer(t)
	ks := generateKeystore(t, h, "recipient")

	plaintext := base64.StdEncoding.EncodeToString([]byte("top secret payload"))
	encRec := doJSON(t, h, http.MethodPost, "/api/v1/crypto/encrypt", encryptRequest{
		Plaintext: plaintext, RecipientCertificate: ks.CertBase64, Filename: "secret.txt",
	})
	if encRec.Code != http.StatusOK {
		t.Fatalf("encrypt: status = %d, body = %s", encRec.Code, encRec.Body.String())
	}
	var encResp encryptResponse
	decodeBody(t, encRec, &encResp)

	decRec := doJSON(t, h, http.MethodPost, "/api/v1/crypto/decrypt", decryptRequest{
		Ciphertext: encResp.Ciphertext, KeyAlias: "recipient",
	})
	if decRec.Code != http.StatusOK {
		t.Fatalf("decrypt: status = %d, body = %s", decRec.Code, decRec.Body.String())
	}
	var decResp decryptResponse
	decodeBody(t, decRec, &decResp)

	gotPlaintext, err := base64.StdEncoding.DecodeString(decResp.Plaintext)
	if err != nil {
		t.Fatalf("failed to decode plaintext: %v", err)
	}
	if string(gotPlaintext) != "top secret payload" {
		t.Errorf("plaintext = %q, want %q", gotPlaintext, "top secret payload")
	}
	if decResp.Filename != "secret.txt" {
		t.Errorf("filename = %q, want secret.txt", decResp.Filename)
	}
}

func TestHash(t *testing.T) {
	h := newTestServer(t)
	data := base64.StdEncoding.EncodeToString([]byte("abc"))
	rec := doJSON(t, h, http.MethodPost, "/api/v1/crypto/hash", hashRequest{Data: data})
	if rec.Code != http.StatusOK {
		t.Fatalf("hash: status = %d, body = %s", rec.Code, rec.Body.String())
	}
	var resp hashResponse
	decodeBody(t, rec, &resp)
	want := "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
	if resp.Hash != want {
		t.Errorf("hash = %s, want %s", resp.Hash, want)
	}
	if resp.InputSizeBytes != 3 {
		t.Errorf("inputSizeBytes = %d, want 3", resp.InputSizeBytes)
	}
}

func TestValidationErrors(t *testing.T) {
	h := newTestServer(t)

	rec := doJSON(t, h, http.MethodPost, "/api/v1/crypto/hash", hashRequest{Data: ""})
	if rec.Code != http.StatusBadRequest {
		t.Errorf("empty data: status = %d, want 400", rec.Code)
	}
	var body errorBody
	decodeBody(t, rec, &body)
	if body.Error.Code != "VALIDATION_ERROR" {
		t.Errorf("code = %s, want VALIDATION_ERROR", body.Error.Code)
	}

	rec = doJSON(t, h, http.MethodPost, "/api/v1/crypto/sign", signRequest{
		Data: "aGVsbG8=", KeyAlias: "x", Mode: "BOGUS",
	})
	if rec.Code != http.StatusBadRequest {
		t.Errorf("bad mode: status = %d, want 400", rec.Code)
	}
}

func TestKeyAliasNotFound(t *testing.T) {
	h := newTestServer(t)
	rec := doJSON(t, h, http.MethodPost, "/api/v1/crypto/sign", signRequest{
		Data: "aGVsbG8=", KeyAlias: "does-not-exist", Mode: "ATTACHED",
	})
	if rec.Code != http.StatusUnprocessableEntity {
		t.Fatalf("status = %d, want 422, body = %s", rec.Code, rec.Body.String())
	}
	var body errorBody
	decodeBody(t, rec, &body)
	if body.Error.Code != "KEY_ALIAS_NOT_FOUND" {
		t.Errorf("code = %s, want KEY_ALIAS_NOT_FOUND", body.Error.Code)
	}
}

func TestFetchRejectsNonHTTPS(t *testing.T) {
	h := newTestServer(t)
	rec := doJSON(t, h, http.MethodPost, "/api/v1/fetch/document", fetchRequest{URL: "http://example.com"})
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400, body = %s", rec.Code, rec.Body.String())
	}
	var body errorBody
	decodeBody(t, rec, &body)
	if body.Error.Code != "INVALID_REQUEST" {
		t.Errorf("code = %s, want INVALID_REQUEST", body.Error.Code)
	}
}

func TestFetchRejectsPrivateAddress(t *testing.T) {
	h := newTestServer(t)
	rec := doJSON(t, h, http.MethodPost, "/api/v1/fetch/document", fetchRequest{URL: "https://localhost/"})
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400, body = %s", rec.Code, rec.Body.String())
	}
}

func TestOperationsListAndDetail(t *testing.T) {
	h := newTestServer(t)
	data := base64.StdEncoding.EncodeToString([]byte("op history test"))
	hashRec := doJSON(t, h, http.MethodPost, "/api/v1/crypto/hash", hashRequest{Data: data})
	var hashResp hashResponse
	decodeBody(t, hashRec, &hashResp)

	listRec := doJSON(t, h, http.MethodGet, "/api/v1/operations?page=0&size=20", nil)
	if listRec.Code != http.StatusOK {
		t.Fatalf("list: status = %d, body = %s", listRec.Code, listRec.Body.String())
	}
	var page pageResponse
	decodeBody(t, listRec, &page)
	if page.TotalElements < 1 {
		t.Errorf("totalElements = %d, want >= 1", page.TotalElements)
	}

	detailRec := doJSON(t, h, http.MethodGet, "/api/v1/operations/"+hashResp.OperationID, nil)
	if detailRec.Code != http.StatusOK {
		t.Fatalf("detail: status = %d, body = %s", detailRec.Code, detailRec.Body.String())
	}
	var detail operationDetailResponse
	decodeBody(t, detailRec, &detail)
	if detail.Status != "SUCCESS" {
		t.Errorf("status = %s, want SUCCESS", detail.Status)
	}
	if detail.Type != "HASH" {
		t.Errorf("type = %s, want HASH", detail.Type)
	}
}

func TestOperationDetailNotFound(t *testing.T) {
	h := newTestServer(t)
	rec := doJSON(t, h, http.MethodGet, "/api/v1/operations/does-not-exist", nil)
	if rec.Code != http.StatusNotFound {
		t.Errorf("status = %d, want 404", rec.Code)
	}
}
