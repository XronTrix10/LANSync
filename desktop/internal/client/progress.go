package client

import (
	"context"
	"fmt"
	"io"
	"time"

	"lansync/internal/models"

	"github.com/wailsapp/wails/v2/pkg/runtime"
)

// ProgressTracker wraps an io.Reader to track speed and emit Wails events
type ProgressTracker struct {
	io.Reader
	ctx         context.Context
	id          string
	filename    string
	total       int64
	transferred int64
	lastEmit    time.Time
	lastBytes   int64
}

func NewProgressTracker(ctx context.Context, reader io.Reader, filename string, total int64, prefix string) *ProgressTracker {
	return &ProgressTracker{
		Reader:   reader,
		ctx:      ctx,
		id:       fmt.Sprintf("%s_%d", prefix, time.Now().UnixNano()),
		filename: filename,
		total:    total,
		lastEmit: time.Now(),
	}
}

func (pt *ProgressTracker) Read(p []byte) (int, error) {
	n, err := pt.Reader.Read(p)
	if n > 0 {
		pt.transferred += int64(n)
		now := time.Now()
		elapsed := now.Sub(pt.lastEmit).Seconds()

		// Throttle UI updates to ~4 times a second
		if elapsed >= 0.25 || pt.transferred == pt.total {
			speedBps := 0.0
			if elapsed > 0 {
				speedBps = float64(pt.transferred-pt.lastBytes) / elapsed
			}

			eta := 0
			if speedBps > 0 {
				eta = int(float64(pt.total-pt.transferred) / speedBps)
			}

			percent := 0
			if pt.total > 0 {
				percent = int((float64(pt.transferred) / float64(pt.total)) * 100)
			}

			payload := models.TransferProgress{
				ID:          pt.id,
				Filename:    pt.filename,
				Total:       pt.total,
				Transferred: pt.transferred,
				Percent:     percent,
				SpeedMBps:   speedBps / 1024 / 1024,
				ETASeconds:  eta,
			}

			runtime.EventsEmit(pt.ctx, "transfer_progress", payload)
			pt.lastEmit = now
			pt.lastBytes = pt.transferred
		}
	}
	return n, err
}

func (pt *ProgressTracker) GetID() string {
	return pt.id
}
