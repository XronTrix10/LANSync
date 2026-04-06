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
  onShareClipboard: () => void;
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
  onShareClipboard,
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

      // 1. Detect Folders securely (Works on Windows / Mac Chromium)
      if (e.dataTransfer.items) {
        for (let i = 0; i < e.dataTransfer.items.length; i++) {
          const item = e.dataTransfer.items[i];
          if (
            item.kind === "file" &&
            typeof item.webkitGetAsEntry === "function"
          ) {
            const entry = item.webkitGetAsEntry();
            if (entry?.isDirectory) {
              hasFolder = true;
            }
          }
        }
      }

      // 2. Extract valid files directly from the reliable HTML5 FileList
      if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
        for (let i = 0; i < e.dataTransfer.files.length; i++) {
          const file = e.dataTransfer.files[i];
          if (file.size >= 4294967296) {
            hasLargeFile = true;
          } else {
            validFiles.push(file);
          }
        }
      }

      if (hasFolder) {
        onError("Please use the 'Folder' button to upload folders");
        return;
      }

      if (hasLargeFile) {
        onError("One or more files are too large (4GB limit).");
      }

      // On Linux, validFiles is empty here, so this safely skips doing anything!
      if (validFiles.length > 0) {
        onDropUpload(validFiles);
      }
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
        onShareClipboard={onShareClipboard}
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
