package clipboard

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net/http"
	"strings"

	"lansync/internal/auth"

	"github.com/atotto/clipboard"
)

type AndroidCallback interface {
	OnClipboardReceived(data []byte, contentType string)
}

type ClipboardManager struct {
	ctx             context.Context
	sessionManager  *auth.SessionManager
	client          *http.Client
	androidCallback AndroidCallback
	isAndroid       bool
}

func NewClipboardManager(ctx context.Context, sm *auth.SessionManager, isAndroid bool) *ClipboardManager {
	return &ClipboardManager{
		ctx:            ctx,
		sessionManager: sm,
		client:         &http.Client{},
		isAndroid:      isAndroid,
	}
}

// ShareDesktopText shares text from Desktop OS
func (cm *ClipboardManager) ShareDesktopText(ip, port string) error {
	if cm.isAndroid {
		return fmt.Errorf("use ShareMobileData from Kotlin")
	}
	text, err := clipboard.ReadAll()
	if err != nil {
		return fmt.Errorf("failed to read OS clipboard: %w", err)
	}
	if text == "" {
		return fmt.Errorf("clipboard is empty")
	}
	return cm.sendData(ip, port, []byte(text), "text/plain")
}

// ShareMobileData shares generic bytes from Android (Kotlin provides the bytes)
func (cm *ClipboardManager) ShareMobileData(ip, port string, data []byte, contentType string) error {
	return cm.sendData(ip, port, data, contentType)
}

func (cm *ClipboardManager) sendData(ip, port string, data []byte, contentType string) error {
	token := cm.sessionManager.GetOutboundToken(ip)
	if token == "" {
		return fmt.Errorf("session expired, please reconnect")
	}

	url := fmt.Sprintf("http://%s:%s/api/clipboard/share", ip, port)
	req, err := http.NewRequestWithContext(cm.ctx, "POST", url, bytes.NewReader(data))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", contentType)

	resp, err := cm.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("server rejected clipboard data (Status %d): %s", resp.StatusCode, string(bodyBytes))
	}
	return nil
}

func (cm *ClipboardManager) HandleClipboardPost(w http.ResponseWriter, r *http.Request) {
	data, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "error reading body", http.StatusInternalServerError)
		return
	}
	contentType := r.Header.Get("Content-Type")

	// 1. Android Case: Tell Kotlin via gomobile
	if cm.androidCallback != nil {
		cm.androidCallback.OnClipboardReceived(data, contentType)
		w.WriteHeader(http.StatusOK)
		return
	}

	// 2. Desktop Case: Write to local OS
	if strings.HasPrefix(contentType, "text/") {
		err = clipboard.WriteAll(string(data))
		if err != nil {
			fmt.Println("failed to write to desktop clipboard:", err)
			http.Error(w, "failed to update clipboard", http.StatusInternalServerError)
			return
		}
	} else {
		fmt.Println("received non-text clipboard data, native implementation pending.")
	}
	w.WriteHeader(http.StatusOK)
}

func (cm *ClipboardManager) SetAndroidCallback(cb AndroidCallback) {
	cm.androidCallback = cb
}
