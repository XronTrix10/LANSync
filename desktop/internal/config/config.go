package config

import (
	"encoding/json"
	"os"
	"path/filepath"

	"github.com/google/uuid"
)

type Config struct {
	DeviceName string `json:"deviceName"`
	SharedDir  string `json:"sharedDir"`
	DeviceID   string `json:"deviceId"`
}

func GetConfigPath() string {
	configDir, err := os.UserConfigDir()
	if err != nil {
		configDir = "." // Fallback to current directory
	}
	dir := filepath.Join(configDir, "lansync")
	os.MkdirAll(dir, 0755)
	return filepath.Join(dir, "config.json")
}

func Load() Config {
	var cfg Config
	data, err := os.ReadFile(GetConfigPath())
	if err == nil {
		json.Unmarshal(data, &cfg)
	}

	needsSave := false

	// Default to computer's hostname if no custom name is set
	if cfg.DeviceName == "" {
		cfg.DeviceName, _ = os.Hostname()
		needsSave = true
	}

	// Generate a persistent UUID if it does not exist
	if cfg.DeviceID == "" {
		cfg.DeviceID = uuid.New().String()
		needsSave = true
	}

	// Immediately persist any newly generated defaults to disk
	if needsSave {
		Save(cfg)
	}

	return cfg
}

func Save(cfg Config) error {
	data, _ := json.MarshalIndent(cfg, "", "  ")
	return os.WriteFile(GetConfigPath(), data, 0644)
}
