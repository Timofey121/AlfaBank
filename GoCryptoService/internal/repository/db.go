package repository

import (
	"database/sql"
	"fmt"
	"os"
	"path/filepath"

	_ "modernc.org/sqlite"

	"gocryptoservice/migrations"
)

func OpenDB(path string) (*sql.DB, error) {
	if dir := filepath.Dir(path); dir != "." {
		if err := os.MkdirAll(dir, 0o700); err != nil {
			return nil, fmt.Errorf("failed to create db directory: %w", err)
		}
	}
	db, err := sql.Open("sqlite", path)
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %w", err)
	}
	db.SetMaxOpenConns(1) // modernc.org/sqlite: single writer avoids SQLITE_BUSY under concurrent access
	if _, err := db.Exec(migrations.SchemaSQL); err != nil {
		db.Close()
		return nil, fmt.Errorf("failed to apply schema: %w", err)
	}
	return db, nil
}
