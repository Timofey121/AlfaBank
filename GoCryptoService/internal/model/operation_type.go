package model

type OperationType string

const (
	OpEncrypt       OperationType = "ENCRYPT"
	OpDecrypt       OperationType = "DECRYPT"
	OpSign          OperationType = "SIGN"
	OpVerify        OperationType = "VERIFY"
	OpHash          OperationType = "HASH"
	OpFetch         OperationType = "FETCH"
	OpKeyGeneration OperationType = "KEY_GENERATION"
)

var allOperationTypes = []OperationType{
	OpEncrypt, OpDecrypt, OpSign, OpVerify, OpHash, OpFetch, OpKeyGeneration,
}

func (t OperationType) IsValid() bool {
	for _, v := range allOperationTypes {
		if v == t {
			return true
		}
	}
	return false
}

func ParseOperationType(raw string) (OperationType, bool) {
	t := OperationType(raw)
	return t, t.IsValid()
}
