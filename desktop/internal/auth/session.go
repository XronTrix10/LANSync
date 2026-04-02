package auth

import (
	"crypto/rand"
	"encoding/hex"
	"sync"
	"time"
)

type Session struct {
	Token    string
	LastSeen time.Time
}

type SessionManager struct {
	mu             sync.RWMutex
	inboundTokens  map[string]Session // Tokens remote devices must provide to US
	outboundTokens map[string]string  // Tokens WE must provide to remote devices
	onDisconnect   func(ip string)    // Callback to alert the UI when a session drops
}

func NewSessionManager(onDisconnect func(ip string)) *SessionManager {
	sm := &SessionManager{
		inboundTokens:  make(map[string]Session),
		outboundTokens: make(map[string]string),
		onDisconnect:   onDisconnect,
	}
	go sm.startSweeper()
	return sm
}

// GenerateToken creates a 32-byte cryptographically secure random hex string
func (sm *SessionManager) GenerateToken() string {
	bytes := make([]byte, 32)
	rand.Read(bytes)
	return hex.EncodeToString(bytes)
}

func (sm *SessionManager) RegisterSession(ip string, inboundToken string, outboundToken string) {
	sm.mu.Lock()
	defer sm.mu.Unlock()
	sm.inboundTokens[ip] = Session{Token: inboundToken, LastSeen: time.Now()}
	sm.outboundTokens[ip] = outboundToken
}

func (sm *SessionManager) ValidateInbound(ip string, providedToken string) bool {
	sm.mu.Lock()
	defer sm.mu.Unlock()

	session, exists := sm.inboundTokens[ip]
	if !exists || session.Token != providedToken {
		return false
	}

	// Update LastSeen heartbeat
	session.LastSeen = time.Now()
	sm.inboundTokens[ip] = session
	return true
}

func (sm *SessionManager) GetOutboundToken(ip string) string {
	sm.mu.RLock()
	defer sm.mu.RUnlock()
	return sm.outboundTokens[ip]
}

func (sm *SessionManager) RemoveSession(ip string) {
	sm.mu.Lock()
	defer sm.mu.Unlock()
	delete(sm.inboundTokens, ip)
	delete(sm.outboundTokens, ip)
}

// startSweeper runs constantly in the background, cleaning up stale connections
func (sm *SessionManager) startSweeper() {
	for {
		time.Sleep(5 * time.Second)
		sm.mu.Lock()

		now := time.Now()
		for ip, session := range sm.inboundTokens {
			// If we haven't heard from them in 15 seconds, sever the connection
			if now.Sub(session.LastSeen) > 15*time.Second {
				delete(sm.inboundTokens, ip)
				delete(sm.outboundTokens, ip)

				// Trigger the UI callback async to avoid deadlocks
				if sm.onDisconnect != nil {
					go sm.onDisconnect(ip)
				}
			}
		}
		sm.mu.Unlock()
	}
}
