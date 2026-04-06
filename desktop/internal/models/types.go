package models

type FileInfo struct {
	Name    string `json:"name"`
	Path    string `json:"path"`
	Size    int64  `json:"size"`
	IsDir   bool   `json:"isDir"`
	ModTime string `json:"modTime"`
}

type TransferProgress struct {
	ID          string  `json:"id"`
	Filename    string  `json:"filename"`
	Total       int64   `json:"total"`
	Transferred int64   `json:"transferred"`
	Percent     int     `json:"percent"`
	SpeedMBps   float64 `json:"speedMBps"`
	ETASeconds  int     `json:"etaSeconds"`
}

// DeviceIdentity is now strictly used for Discovery (Broadcast/Ping)
type DeviceIdentity struct {
	IP         string `json:"ip"`
	Port       string `json:"port"`
	DeviceName string `json:"deviceName"`
	OS         string `json:"os"`
	Type       string `json:"type"`
}

// ConnectionRequest is the initial "Knock" payload
type ConnectionRequest struct {
	DeviceIdentity
	TokenForB string `json:"tokenForB"` // Token the requested device must use to talk to us
}

// ConnectionResponse is the "Acceptance" payload
type ConnectionResponse struct {
	Accepted   bool   `json:"accepted"`
	TokenForA  string `json:"tokenForA"` // Token we must use to talk to the requested device
	DeviceName string `json:"deviceName"`
}
