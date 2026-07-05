const API = "/api/v1";

document.querySelectorAll(".tab-btn").forEach(btn => {
    btn.addEventListener("click", () => {
        document.querySelectorAll(".tab-btn").forEach(b => b.classList.remove("active"));
        document.querySelectorAll(".tab-panel").forEach(p => p.classList.remove("active"));
        btn.classList.add("active");
        document.getElementById("panel-" + btn.dataset.tab).classList.add("active");
    });
});

async function apiJson(url, method, body) {
    const res = await fetch(url, {
        method,
        headers: body ? {"Content-Type": "application/json"} : undefined,
        body: body ? JSON.stringify(body) : undefined,
    });
    const data = await res.json();
    if (!res.ok) {
        const msg = data?.error?.message || "Request failed";
        throw new Error(msg);
    }
    return data;
}

async function encodeFile(file) {
    const form = new FormData();
    form.append("file", file);
    const res = await fetch(`${API}/convert/encode`, {method: "POST", body: form});
    const data = await res.json();
    if (!res.ok) throw new Error(data?.error?.message || "Encode failed");
    return data.base64;
}

async function encodeText(text) {
    const form = new FormData();
    form.append("text", text);
    const res = await fetch(`${API}/convert/encode`, {method: "POST", body: form});
    const data = await res.json();
    if (!res.ok) throw new Error(data?.error?.message || "Encode failed");
    return data.base64;
}

async function downloadBase64(base64, filename) {
    const res = await fetch(`${API}/convert/decode`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({base64, filename}),
    });
    if (!res.ok) {
        const data = await res.json();
        throw new Error(data?.error?.message || "Decode failed");
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
}

function initBinaryInputs(root) {
    root.querySelectorAll("[data-binary-input]").forEach(field => {
        const name = field.dataset.binaryInput;
        const modes = field.dataset.modes.split(",");
        const label = field.dataset.textLabel || name;
        const optional = field.dataset.optional === "true";

        const modeNames = {file: "Файл", text: "Текст", base64: "Base64"};
        const switchDiv = document.createElement("div");
        switchDiv.className = "mode-switch";
        modes.forEach((m, i) => {
            const id = `${name}-${m}`;
            switchDiv.innerHTML += `
                <label><input type="radio" name="${name}-mode" value="${m}" ${i === 0 ? "checked" : ""}> ${modeNames[m]}</label>
            `;
        });

        const labelDiv = document.createElement("div");
        labelDiv.className = "field-label";
        labelDiv.textContent = label + (optional ? " (необязательно)" : "");

        const inputsDiv = document.createElement("div");
        const renderInput = (mode) => {
            inputsDiv.innerHTML = "";
            if (mode === "file") {
                inputsDiv.innerHTML = `<input type="file" data-role="file">`;
            } else if (mode === "text") {
                inputsDiv.innerHTML = `<textarea data-role="text" placeholder="Введите текст..."></textarea>`;
            } else if (mode === "base64") {
                inputsDiv.innerHTML = `<textarea data-role="base64" placeholder="Вставьте base64..."></textarea>`;
            }
        };
        renderInput(modes[0]);

        field.innerHTML = "";
        field.appendChild(labelDiv);
        field.appendChild(switchDiv);
        field.appendChild(inputsDiv);

        switchDiv.querySelectorAll("input[type=radio]").forEach(r => {
            r.addEventListener("change", () => renderInput(r.value));
        });

        field.getFilename = () => {
            const activeMode = switchDiv.querySelector("input[type=radio]:checked").value;
            if (activeMode !== "file") return null;
            const fileInput = inputsDiv.querySelector("[data-role=file]");
            const f = fileInput.files[0];
            return f ? f.name : null;
        };

        field.getBase64 = async () => {
            const activeMode = switchDiv.querySelector("input[type=radio]:checked").value;
            if (activeMode === "file") {
                const fileInput = inputsDiv.querySelector("[data-role=file]");
                const f = fileInput.files[0];
                if (!f) {
                    if (optional) return null;
                    throw new Error(`${label}: выберите файл`);
                }
                return await encodeFile(f);
            } else if (activeMode === "text") {
                const val = inputsDiv.querySelector("[data-role=text]").value;
                if (!val) {
                    if (optional) return null;
                    throw new Error(`${label}: введите текст`);
                }
                return await encodeText(val);
            } else {
                const val = inputsDiv.querySelector("[data-role=base64]").value.trim();
                if (!val) {
                    if (optional) return null;
                    throw new Error(`${label}: вставьте base64`);
                }
                return val;
            }
        };
    });
}

initBinaryInputs(document);

function getBinaryInput(form, name) {
    return form.querySelector(`[data-binary-input="${name}"]`).getBase64();
}

function resultBox(id, ok) {
    const el = document.getElementById(id);
    el.className = "result " + (ok ? "ok" : "err");
    el.replaceChildren();
    return el;
}

function showResult(id, ok, data) {
    const el = resultBox(id, ok);
    const pre = document.createElement("pre");
    pre.textContent = JSON.stringify(data, null, 2);
    el.appendChild(pre);
    return el;
}

function base64ToText(base64) {
    try {
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return new TextDecoder("utf-8", {fatal: true}).decode(bytes);
    } catch (e) {
        return null;
    }
}

function addDecodedText(el, base64) {
    const text = base64ToText(base64);
    if (text === null) return;
    const pre = document.createElement("pre");
    pre.textContent = text;
    const label = document.createElement("div");
    label.className = "field-label";
    label.textContent = "Расшифрованный текст:";
    el.insertBefore(pre, el.firstChild);
    el.insertBefore(label, pre);
}

function addDownloadButton(el, base64, filename) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "download-btn";
    btn.textContent = `Скачать (${filename})`;
    btn.addEventListener("click", () => downloadBase64(base64, filename).catch(e => alert(e.message)));
    el.appendChild(btn);
}

document.getElementById("form-encrypt").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = e.target;
    try {
        const plaintextField = form.querySelector('[data-binary-input="plaintext"]');
        const plaintext = await getBinaryInput(form, "plaintext");
        const filename = plaintextField.getFilename ? plaintextField.getFilename() : null;
        const recipientCertificate = await getBinaryInput(form, "recipientCertificate");
        const data = await apiJson(`${API}/crypto/encrypt`, "POST", {plaintext, recipientCertificate, filename});
        const el = resultBox("result-encrypt", true);
        addDownloadButton(el, data.ciphertext, "ciphertext.bin");
    } catch (err) {
        showResult("result-encrypt", false, {error: err.message});
    }
});

document.getElementById("form-decrypt").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = e.target;
    try {
        const ciphertext = await getBinaryInput(form, "ciphertext");
        const keyAlias = form.keyAlias.value;
        const data = await apiJson(`${API}/crypto/decrypt`, "POST", {ciphertext, keyAlias});
        const el = resultBox("result-decrypt", true);
        if (!data.filename) {
            addDecodedText(el, data.plaintext);
        } else {
            addDownloadButton(el, data.plaintext, data.filename);
        }
    } catch (err) {
        showResult("result-decrypt", false, {error: err.message});
    }
});

document.getElementById("form-sign").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = e.target;
    try {
        const data_ = await getBinaryInput(form, "data");
        const keyAlias = form.keyAlias.value;
        const mode = form.mode.value;
        const data = await apiJson(`${API}/crypto/sign`, "POST", {data: data_, keyAlias, mode});
        const el = resultBox("result-sign", true);
        addDownloadButton(el, data.signature, "signature.bin");
    } catch (err) {
        showResult("result-sign", false, {error: err.message});
    }
});

document.getElementById("form-verify").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = e.target;
    try {
        const signature = await getBinaryInput(form, "signature");
        const data_ = await getBinaryInput(form, "data");
        const mode = form.mode.value;
        const body = {signature, mode};
        if (data_) body.data = data_;
        const data = await apiJson(`${API}/crypto/verify`, "POST", body);
        showResult("result-verify", true, {
            valid: data.valid,
            signerSubject: data.signerSubject,
            signerSerial: data.signerSerial,
            certNotBefore: data.certNotBefore,
            certNotAfter: data.certNotAfter,
        });
    } catch (err) {
        showResult("result-verify", false, {error: err.message});
    }
});

document.getElementById("form-hash").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = e.target;
    try {
        const data_ = await getBinaryInput(form, "data");
        const data = await apiJson(`${API}/crypto/hash`, "POST", {data: data_});
        showResult("result-hash", true, {
            algorithm: data.algorithm,
            hash: data.hash,
            inputSizeBytes: data.inputSizeBytes,
        });
    } catch (err) {
        showResult("result-hash", false, {error: err.message});
    }
});

document.getElementById("form-fetch").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = e.target;
    try {
        const url = form.url.value;
        const rawTimeoutSeconds = form.timeoutSeconds.value;
        const timeoutSeconds = rawTimeoutSeconds === "" || Number.isNaN(Number(rawTimeoutSeconds))
            ? 30
            : Number(rawTimeoutSeconds);
        const data = await apiJson(`${API}/fetch/document`, "POST", {url, timeoutSeconds});
        const el = showResult("result-fetch", true, {
            contentType: data.contentType,
            sizeBytes: data.sizeBytes,
            httpStatus: data.httpStatus,
        });
        const ext = (data.contentType || "").includes("json") ? "json" : "bin";
        addDownloadButton(el, data.content, `fetched.${ext}`);
    } catch (err) {
        showResult("result-fetch", false, {error: err.message});
    }
});

document.getElementById("form-keystore").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = e.target;
    try {
        const alias = form.alias.value;
        const cn = form.cn.value;
        const validityDays = Number(form.validityDays.value) || 365;
        const data = await apiJson(`${API}/admin/generate-keystore`, "POST", {alias, cn, validityDays});
        const el = showResult("result-keystore", true, {
            alias: data.alias,
            subject: data.subject,
            serialNumber: data.serialNumber,
            notBefore: data.notBefore,
            notAfter: data.notAfter,
        });
        addDownloadButton(el, data.certBase64, `${alias}-cert.der`);
    } catch (err) {
        showResult("result-keystore", false, {error: err.message});
    }
});
