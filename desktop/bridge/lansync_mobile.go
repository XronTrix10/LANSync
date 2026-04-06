package bridge

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"lansync/internal/auth"
	"lansync/internal/client"
	"lansync/internal/clipboard"
	"lansync/internal/models"
	"lansync/internal/server"

	_ "golang.org/x/mobile/bind"
)

var (
	ctx              context.Context
	cancel           context.CancelFunc
	sessionManager   *auth.SessionManager
	androidClient    *client.AndroidClient
	desktopServer    *server.DesktopServer
	clipboardManager *clipboard.ClipboardManager
)

type BridgeCallback interface {
	OnDeviceDropped(ip string)
	OnClipboardDataReceived(data []byte, contentType string)
}

func StartupWithCallback(cb BridgeCallback) {
	ctx, cancel = context.WithCancel(context.Background())

	sessionManager = auth.NewSessionManager(func(droppedIP string) {
		cb.OnDeviceDropped(droppedIP)
	})

	clipboardManager = clipboard.NewClipboardManager(ctx, sessionManager, true)
	clipboardManager.SetAndroidCallback(&androidClipboardProxy{cb})

	androidClient = client.NewAndroidClient(ctx, sessionManager)
	desktopServer = server.NewDesktopServer(sessionManager, clipboardManager)
	desktopServer.SetContext(ctx)

	go desktopServer.Start("34932")
}

type androidClipboardProxy struct {
	cb BridgeCallback
}

func (p *androidClipboardProxy) OnClipboardReceived(data []byte, contentType string) {
	p.cb.OnClipboardDataReceived(data, contentType)
}

func RequestConnection(ip string, port string) (string, error) {
	return androidClient.RequestConnection(ip, port)
}

func DisconnectDevice(ip string) {
	// FIX: Fire a polite disconnect signal to the PC so it drops instantly!
	token := sessionManager.GetOutboundToken(ip)
	if token != "" {
		req, _ := http.NewRequest("POST", fmt.Sprintf("http://%s:34931/api/disconnect", ip), nil)
		req.Header.Set("Authorization", "Bearer "+token)
		client := http.Client{Timeout: 2 * time.Second}
		go client.Do(req)
	}
	sessionManager.RemoveSession(ip)
}

func GetSessionToken(ip string) string {
	return sessionManager.GetOutboundToken(ip)
}

func ShareMobileClipboard(ip string, port string, data []byte, contentType string) error {
	return clipboardManager.ShareMobileData(ip, port, data, contentType)
}

func GetRemoteFilesJson(ip string, port string, path string) (string, error) {
	result, err := androidClient.GetRemoteFiles(ip, port, path)
	if err != nil {
		return "", err
	}

	jsonBytes, err := json.Marshal(result)
	if err != nil {
		return "", err
	}

	return string(jsonBytes), nil
}

func MakeDirectory(ip string, port string, dir string, name string) error {
	return androidClient.MakeDirectory(ip, port, dir, name)
}

// ─── NATIVE MOBILE BACKEND SERVER ──────────────────────────────────────────

func StartMobileServer(exposedDir string) {
	go func() {
		mux := http.NewServeMux()

		// 1. AUTH MIDDLEWARE (Validates the PC's token & strips IPv6 wrapper)
		authMiddleware := func(next http.HandlerFunc) http.HandlerFunc {
			return func(w http.ResponseWriter, r *http.Request) {
				clientIP, _, err := net.SplitHostPort(r.RemoteAddr)
				if err != nil {
					clientIP = r.RemoteAddr
				}

				// FIX: Strip the Android IPv4-mapped IPv6 prefix so the session matches!
				clientIP = strings.TrimPrefix(clientIP, "::ffff:")
				if clientIP == "::1" {
					clientIP = "127.0.0.1"
				}

				token := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
				if !sessionManager.ValidateInbound(clientIP, token) {
					http.Error(w, "Unauthorized", http.StatusUnauthorized)
					return
				}
				next(w, r)
			}
		}

		// 2. DISCOVERY ROUTE
		mux.HandleFunc("/api/identify", func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			json.NewEncoder(w).Encode(models.DeviceIdentity{
				DeviceName: "Android Phone",
				OS:         "android",
				Type:       "mobile",
				Port:       "34931",
			})
		})

		// 3. HANDSHAKE / TOKEN EXCHANGE
		mux.HandleFunc("/api/connect", func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			var req models.ConnectionRequest
			if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
				http.Error(w, "Bad Request", http.StatusBadRequest)
				return
			}

			clientIP, _, err := net.SplitHostPort(r.RemoteAddr)
			if err != nil {
				clientIP = r.RemoteAddr
			}

			// FIX: Strip the IPv6 prefix here too!
			clientIP = strings.TrimPrefix(clientIP, "::ffff:")
			if clientIP == "::1" {
				clientIP = "127.0.0.1"
			}

			tokenForA := sessionManager.GenerateToken()
			sessionManager.RegisterSession(clientIP, tokenForA, req.TokenForB)

			json.NewEncoder(w).Encode(models.ConnectionResponse{
				Accepted:  true,
				TokenForA: tokenForA,
			})
		})

		// 4. HEARTBEAT
		mux.HandleFunc("/api/ping", authMiddleware(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusOK)
		}))

		// 5. LIST FILES
		mux.HandleFunc("/api/files/list", authMiddleware(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")

			requestedPath := r.URL.Query().Get("path")
			if requestedPath == "" || requestedPath == "/" {
				requestedPath = ""
			}

			if strings.Contains(requestedPath, "..") {
				http.Error(w, "Invalid path", http.StatusForbidden)
				return
			}

			fullPath := filepath.Join(exposedDir, requestedPath)
			entries, err := os.ReadDir(fullPath)

			if err != nil {
				json.NewEncoder(w).Encode(map[string]interface{}{
					"path":   requestedPath,
					"parent": filepath.Dir(requestedPath),
					"files":  []models.FileInfo{},
				})
				return
			}

			var files []models.FileInfo
			for _, e := range entries {
				info, err := e.Info()
				if err != nil {
					continue
				}

				relPath := e.Name()
				if requestedPath != "" {
					relPath = requestedPath + "/" + e.Name()
				}

				files = append(files, models.FileInfo{
					Name:    e.Name(),
					Path:    relPath,
					Size:    info.Size(),
					IsDir:   e.IsDir(),
					ModTime: info.ModTime().Format("2006-01-02 15:04"),
				})
			}

			parent := filepath.Dir(requestedPath)
			if requestedPath == "" {
				parent = ""
			}
			if files == nil {
				files = []models.FileInfo{}
			}

			json.NewEncoder(w).Encode(map[string]interface{}{
				"path":   requestedPath,
				"parent": parent,
				"files":  files,
			})
		}))

		// 6. DOWNLOAD
		mux.HandleFunc("/api/files/download", authMiddleware(func(w http.ResponseWriter, r *http.Request) {
			requestedPath := r.URL.Query().Get("path")
			if strings.Contains(requestedPath, "..") {
				http.Error(w, "Invalid path", http.StatusForbidden)
				return
			}
			fullPath := filepath.Join(exposedDir, requestedPath)
			http.ServeFile(w, r, fullPath)
		}))

		// 7. UPLOAD
		mux.HandleFunc("/api/files/upload", authMiddleware(func(w http.ResponseWriter, r *http.Request) {
			r.ParseMultipartForm(10 << 20)
			dir := r.URL.Query().Get("dir")

			file, handler, err := r.FormFile("files")
			if err != nil {
				http.Error(w, err.Error(), http.StatusBadRequest)
				return
			}
			defer file.Close()

			if strings.Contains(dir, "..") {
				http.Error(w, "Invalid path", http.StatusForbidden)
				return
			}

			destDir := filepath.Join(exposedDir, dir)
			os.MkdirAll(destDir, 0755)

			destPath := filepath.Join(destDir, handler.Filename)
			dst, err := os.Create(destPath)
			if err != nil {
				http.Error(w, err.Error(), http.StatusInternalServerError)
				return
			}
			defer dst.Close()

			io.Copy(dst, file)
			w.WriteHeader(http.StatusOK)
		}))

		// 8. MKDIR
		mux.HandleFunc("/api/files/mkdir", authMiddleware(func(w http.ResponseWriter, r *http.Request) {
			dir := r.URL.Query().Get("dir")
			name := r.URL.Query().Get("name")
			if strings.Contains(dir, "..") || strings.Contains(name, "..") {
				http.Error(w, "Invalid path", http.StatusForbidden)
				return
			}
			os.MkdirAll(filepath.Join(exposedDir, dir, name), 0755)
			w.WriteHeader(http.StatusOK)
		}))

		// 9. CLIPBOARD RECEIVER
		mux.HandleFunc("/api/clipboard/share", authMiddleware(func(w http.ResponseWriter, r *http.Request) {
			if r.Method != http.MethodPost {
				http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
				return
			}
			clipboardManager.HandleClipboardPost(w, r)
		}))

		// 10. CORS MIDDLEWARE
		corsHandler := func(next http.Handler) http.Handler {
			return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.Header().Set("Access-Control-Allow-Origin", "*")
				w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
				w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type")

				if r.Method == "OPTIONS" {
					w.WriteHeader(http.StatusOK)
					return
				}
				next.ServeHTTP(w, r)
			})
		}

		fmt.Println("Starting Native Mobile API Server on port 34931, exposing:", exposedDir)
		err := http.ListenAndServe(":34931", corsHandler(mux))
		if err != nil {
			fmt.Println("Mobile server crashed:", err)
		}
	}()
}

func Shutdown() {
	if cancel != nil {
		cancel()
	}
}
