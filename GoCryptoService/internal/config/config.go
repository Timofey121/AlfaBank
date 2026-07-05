package config

import (
	"fmt"
	"os"
)

type Config struct {
	KeystorePassword string
	KeystorePath     string
	DBPath           string
	Port             string
}

func Load() (Config, error) {
	password := os.Getenv("KEYSTORE_PASSWORD")
	if password == "" {
		return Config{}, fmt.Errorf("KEYSTORE_PASSWORD environment variable is required")
	}
	return Config{
		KeystorePassword: password,
		KeystorePath:     envOr("KEYSTORE_PATH", "./certs"),
		DBPath:           envOr("DB_PATH", "./data/cryptodb.sqlite"),
		Port:             envOr("PORT", "8080"),
	}, nil
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
