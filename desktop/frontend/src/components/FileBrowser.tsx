import {
  CloudUpload,
  FolderPlus,
  FolderUp,
  Layers,
  Loader2,
  Upload,
} from "lucide-react";
import { useCallback, useRef, useState } from "react";
import type { FileInfo } from "../types";
import { BrowserToolbar } from "./BrowserToolbar";
import { CreateFolderModal } from "./CreateFolderModal";
import { FileRow } from "./FileRow";

interface Props {
  activeDeviceIP: string | null;
  files: FileInfo[];
  currentPath: string;
  parentPath: string;
  deviceRootPath: string;
  loading: boolean;
  uploading: boolean;
  onNavigate: (path: string) => void;
  onDownload: (file: FileInfo) => void;
  onUploadFiles: () => void;
  onUploadFolder: () => void;
  onDropUpload: (files: File[]) => void;
  onCreateFolder: (folderName: string) => void;
  onError: (msg: string) => void;
}

export function FileBrowser({
  activeDeviceIP,
  files,
  currentPath,
  parentPath,
  deviceRootPath,
  loading,
  uploading,
  onNavigate,
  onDownload,
  onUploadFiles,
  onUploadFolder,
  onDropUpload,
  onCreateFolder,
  onError,
}: Props) {
  const [isDragging, setIsDragging] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const dragCounter = useRef(0);

  const disabled = loading || uploading;

  // ── Drag and drop handlers ──────────────────────────────────────────────────
  const handleDragEnter = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      if (!activeDeviceIP) return;

      dragCounter.current++;

      // Blindly show overlay (Linux hides e.dataTransfer.types for security here)
      setIsDragging(true);
    },
    [activeDeviceIP],
  );

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter.current--;
    if (dragCounter.current === 0) setIsDragging(false);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragging(false);
      dragCounter.current = 0;

      if (!e.dataTransfer) return;

      const validFiles: File[] = [];
      let hasFolder = false;
      let hasLargeFile = false;

      // ── CROSS-PLATFORM PARSER (Windows + Mac + Linux) ──
      if (e.dataTransfer.items && e.dataTransfer.items.length > 0) {
        for (const item of Array.from(e.dataTransfer.items)) {
          if (item.kind === "file") {
            // 1. Windows/Mac strict folder check
            if (typeof item.webkitGetAsEntry === "function") {
              const entry = item.webkitGetAsEntry();
              if (entry?.isDirectory) {
                hasFolder = true;
                continue; // Skip folders
              }
            }

            // 2. Extract file
            const file = item.getAsFile();
            if (file) {
              // Linux folder check fallback
              if (!file.type && file.size % 4096 === 0 && file.size <= 102400) {
                hasFolder = true;
                continue;
              }

              if (file.size >= 4294967296) hasLargeFile = true;
              else validFiles.push(file);
            }
          }
        }
      }
      // Fallback for strict Linux WebKit environments
      else if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
        for (const file of Array.from(e.dataTransfer.files)) {
          if (!file.type && file.size % 4096 === 0 && file.size <= 102400) {
            hasFolder = true;
            continue;
          }

          if (file.size >= 4294967296) hasLargeFile = true;
          else validFiles.push(file);
        }
      }

      if (hasFolder)
        onError("Please use the 'Folder' button to upload folders");
      if (hasLargeFile) onError("One or more files are too large (4GB limit).");
      if (validFiles.length > 0) onDropUpload(validFiles);
    },
    [onDropUpload, onError],
  );

  // ── Empty / no-device state ────────────────────────────────────────────────
  if (!activeDeviceIP) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-4 bg-bg-base text-[#3d4d63]">
        <div className="relative">
          <div className="w-20 h-20 rounded-2xl border-2 border-dashed border-[#1e2535] flex items-center justify-center">
            <Layers size={32} strokeWidth={1} />
          </div>
          <div className="absolute -top-1.5 -right-1.5 w-5 h-5 rounded-full bg-accent/15 border border-accent/30 flex items-center justify-center">
            <span className="text-[8px] text-accent font-bold">?</span>
          </div>
        </div>
        <div className="text-center">
          <p className="text-[15px] font-semibold text-[#8090a8]">
            No device selected
          </p>
          <p className="text-[12px] text-[#3d4d63] mt-1">
            Connect a device from the sidebar to browse files
          </p>
        </div>
      </div>
    );
  }

  return (
    <div
      className="flex-1 flex flex-col bg-bg-base overflow-hidden relative h-full min-h-0 w-full"
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={(e) => {
        e.preventDefault();
        e.stopPropagation();
        e.dataTransfer.dropEffect = "copy";
      }}
      onDrop={handleDrop}
    >
      {/* ── Drag Overlay ── */}
      {/* Fallback dark tint ensures text is readable on Linux where blur is disabled */}
      {isDragging && (
        <div className="absolute inset-0 z-40 bg-bg-base/75 backdrop-blur-md pointer-events-none transition-all duration-200" />
      )}
      {isDragging && (
        <div className="absolute inset-3 z-50 rounded-2xl border-2 border-dashed border-accent/60 bg-accent/5 backdrop-blur-md flex flex-col items-center justify-center gap-3 pointer-events-none">
          <div className="w-16 h-16 rounded-2xl bg-accent/10 border border-accent/30 flex items-center justify-center">
            <CloudUpload size={28} className="text-accent" strokeWidth={1.5} />
          </div>
          <div className="text-center">
            <p className="text-[15px] font-semibold text-accent">
              Drop to upload
            </p>
          </div>
        </div>
      )}

      {/* ── Modals & Toolbar ── */}
      <CreateFolderModal
        isOpen={showCreateModal}
        loading={loading}
        existingFiles={files}
        onClose={() => setShowCreateModal(false)}
        onSubmit={(name) => {
          onCreateFolder(name);
          setShowCreateModal(false);
        }}
      />

      <BrowserToolbar
        currentPath={currentPath}
        parentPath={parentPath}
        deviceRootPath={deviceRootPath}
        loading={loading}
        uploading={uploading}
        disabled={disabled}
        onNavigate={onNavigate}
        onUploadFiles={onUploadFiles}
        onUploadFolder={onUploadFolder}
        onCreateFolderClick={() => setShowCreateModal(true)}
      />

      {/* ── Header Row ── */}
      <div className="grid grid-cols-[1fr_100px_80px] px-5 py-2 border-b border-[#1e2535] bg-surface/40 shrink-0">
        {["Name", "Size", ""].map((col, i) => (
          <span
            key={i}
            className="text-[9px] font-bold tracking-[0.12em] uppercase text-[#3d4d63]"
          >
            {col}
          </span>
        ))}
      </div>

      {/* ── File List Area ── */}
      <div className="flex-1 overflow-y-auto scrollbar-thin relative">
        {loading && files.length === 0 && (
          <div className="flex items-center justify-center gap-2 py-16 text-[#3d4d63]">
            <Loader2 size={16} className="animate-spin" />
            <span className="text-[12px]">Loading…</span>
          </div>
        )}

        {/* ── Empty State ── */}
        {!loading && files.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full gap-4 pb-16 text-[#3d4d63]">
            <div className="flex flex-col items-center gap-2">
              <span className="text-4xl mb-1">📭</span>
              <p className="text-[13px] text-[#8090a8] font-medium">
                This folder is empty
              </p>
            </div>
            <div className="flex items-center gap-3 mt-2">
              <button
                onClick={onUploadFiles}
                disabled={disabled}
                className="flex items-center gap-2 px-4 py-2 rounded-xl text-[12px] font-semibold text-accent bg-accent/8 border border-accent/25 hover:bg-accent/15 hover:border-accent/40 disabled:opacity-40 transition-all"
              >
                {uploading ? (
                  <Loader2 size={14} className="animate-spin" />
                ) : (
                  <Upload size={14} />
                )}{" "}
                Upload Files
              </button>

              <button
                onClick={onUploadFolder}
                disabled={disabled}
                className="flex items-center gap-2 px-4 py-2 rounded-xl text-[12px] font-semibold text-[#00c9a7] bg-[#00c9a7]/8 border border-[#00c9a7]/25 hover:bg-[#00c9a7]/15 hover:border-[#00c9a7]/40 disabled:opacity-40 transition-all"
              >
                <FolderUp size={14} /> Upload Folder
              </button>

              <button
                onClick={() => setShowCreateModal(true)}
                disabled={disabled}
                className="flex items-center gap-2 px-4 py-2 rounded-xl text-[12px] font-semibold text-accent bg-accent/8 border border-accent/25 hover:bg-accent/15 hover:border-accent/40 disabled:opacity-40 transition-all"
              >
                <FolderPlus size={14} /> Create Folder
              </button>
            </div>
          </div>
        )}

        {files.map((file, idx) => (
          <FileRow
            key={`${file.path}-${idx}`}
            file={file}
            onNavigate={onNavigate}
            onDownload={onDownload}
            disabled={disabled}
          />
        ))}
      </div>
    </div>
  );
}
