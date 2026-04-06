import {
  ChevronUp,
  ClipboardPaste,
  FolderPlus,
  FolderUp,
  Loader2,
  RefreshCw,
  Upload
} from "lucide-react";
import { useEffect, useState } from "react";

interface Props {
  currentPath: string;
  parentPath: string;
  deviceRootPath: string;
  loading: boolean;
  uploading: boolean;
  disabled: boolean;
  onNavigate: (path: string) => void;
  onUploadFiles: () => void;
  onUploadFolder: () => void;
  onCreateFolderClick: () => void;
  onShareClipboard: () => void;
}

export function BrowserToolbar({
  currentPath,
  parentPath,
  deviceRootPath,
  loading,
  uploading,
  disabled,
  onNavigate,
  onUploadFiles,
  onUploadFolder,
  onCreateFolderClick,
  onShareClipboard
}: Props) {
  const [showFolderMenu, setShowFolderMenu] = useState(false);

  // Close dropdown on outside click
  useEffect(() => {
    const handleClickOutside = () => setShowFolderMenu(false);
    if (showFolderMenu) document.addEventListener("click", handleClickOutside);
    return () => document.removeEventListener("click", handleClickOutside);
  }, [showFolderMenu]);

  // ── Path Calculations ──
  const normPath = currentPath ? currentPath.replace(/\\/g, "/") : "";
  const normRoot = deviceRootPath ? deviceRootPath.replace(/\\/g, "/") : "";

  let displayPath = normPath;
  if (normRoot && normPath.startsWith(normRoot)) {
    displayPath = normPath.substring(normRoot.length);
  }
  if (!displayPath.startsWith("/")) displayPath = "/" + displayPath;

  const pathSegments = displayPath.split("/").filter(Boolean);
  const isRoot = normPath === normRoot;
  const canGoUp = !isRoot && parentPath;

  const MAX_SEGMENTS = 5;
  const showEllipsis = pathSegments.length > MAX_SEGMENTS;
  const visibleSegments = showEllipsis
    ? pathSegments.slice(-MAX_SEGMENTS)
    : pathSegments;

  const buildAbsolutePath = (relativePath: string) => {
    if (!normRoot) return relativePath;
    if (normRoot.endsWith("/")) return normRoot.slice(0, -1) + relativePath;
    return normRoot + relativePath;
  };

  return (
    <div className="flex items-center gap-2 px-4 py-2.5 border-b border-[#1e2535] bg-surface/60 shrink-0">
      <button
        onClick={() => onNavigate(parentPath || "/")}
        disabled={!canGoUp || disabled}
        className="p-1.5 rounded-lg text-[#3d4d63] border border-transparent hover:text-[#dde4f0] hover:bg-panel hover:border-[#1e2535] disabled:opacity-25 disabled:cursor-not-allowed transition-all"
      >
        <ChevronUp size={16} />
      </button>

      {/* ── Breadcrumbs ── */}
      <div className="flex-1 flex items-center gap-1 overflow-x-auto hide-scrollbar min-w-0">
        <button
          onClick={() => onNavigate("/")}
          className="text-[11px] font-mono text-[#3d4d63] hover:text-accent transition-colors shrink-0 px-1 py-0.5 rounded hover:bg-accent/8"
        >
          /
        </button>

        {showEllipsis && (
          <span className="flex items-center gap-1 shrink-0">
            <span className="text-[#1e2535] text-[11px]">/</span>
            <span className="text-[11px] font-mono px-1 py-0.5 text-[#8090a8] select-none">
              ...
            </span>
          </span>
        )}

        {visibleSegments.map((seg, i) => {
          const originalIndex =
            pathSegments.length - visibleSegments.length + i;
          const relativeSegPath =
            "/" + pathSegments.slice(0, originalIndex + 1).join("/");
          const absoluteSegPath = buildAbsolutePath(relativeSegPath);
          const isLast = originalIndex === pathSegments.length - 1;

          return (
            <span
              key={originalIndex}
              className="flex items-center gap-1 shrink-0 min-w-0"
            >
              <span className="text-[#1e2535] text-[11px]">/</span>
              <button
                onClick={() => !isLast && onNavigate(absoluteSegPath)}
                title={seg}
                className={`
                  text-[11px] font-mono px-1 py-0.5 rounded transition-colors truncate max-w-30
                  ${isLast ? "text-[#dde4f0] cursor-default" : "text-[#3d4d63] hover:text-accent hover:bg-accent/8 cursor-pointer"}
                `}
              >
                {seg}
              </button>
            </span>
          );
        })}
      </div>

      {/* ── Actions ── */}
      <div className="flex items-center gap-1.5 shrink-0">
        <button
          onClick={() => onNavigate(currentPath)}
          disabled={disabled}
          className="p-1.5 rounded-lg text-[#3d4d63] border border-transparent hover:text-[#dde4f0] hover:bg-panel hover:border-[#1e2535] disabled:opacity-30 transition-all"
        >
          <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
        </button>
        <div className="w-px h-4 bg-[#1e2535]" />

        <button
          onClick={onShareClipboard}
          disabled={disabled}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[12px] font-semibold text-[#a78bfa] bg-[#a78bfa]/8 border border-[#a78bfa]/25 hover:bg-[#a78bfa]/15 hover:border-[#a78bfa]/40 disabled:opacity-40 transition-all"
        >
          <ClipboardPaste size={12} /> Share Clipboard
        </button>

        <button
          onClick={onUploadFiles}
          disabled={disabled}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[12px] font-semibold text-accent bg-accent/8 border border-accent/25 hover:bg-accent/15 hover:border-accent/40 disabled:opacity-40 transition-all"
        >
          {uploading ? (
            <Loader2 size={12} className="animate-spin" />
          ) : (
            <Upload size={12} />
          )}{" "}
          Files
        </button>

        <div className="relative" onClick={(e) => e.stopPropagation()}>
          <button
            onClick={() => setShowFolderMenu(!showFolderMenu)}
            disabled={disabled}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[12px] font-semibold text-[#00c9a7] bg-[#00c9a7]/8 border border-[#00c9a7]/25 hover:bg-[#00c9a7]/15 hover:border-[#00c9a7]/40 disabled:opacity-40 transition-all"
          >
            <FolderUp size={12} /> Folder
          </button>

          {showFolderMenu && (
            <div className="absolute top-full right-0 mt-1.5 w-44 bg-panel border border-[#1e2535] rounded-xl shadow-2xl py-1.5 px-1.5 z-50 animate-in fade-in zoom-in-95 duration-100">
              <button
                onClick={() => {
                  setShowFolderMenu(false);
                  onUploadFolder();
                }}
                className="w-full rounded-lg flex items-center gap-2.5 px-3 py-2 text-[12px] font-medium text-[#dde4f0] hover:bg-[#00c9a7]/10 hover:text-[#00c9a7] transition-colors"
              >
                <FolderUp size={13} /> Upload Folder
              </button>
              <div className="h-px w-full bg-[#1e2535] my-1" />
              <button
                onClick={() => {
                  setShowFolderMenu(false);
                  onCreateFolderClick();
                }}
                className="w-full rounded-lg flex items-center gap-2.5 px-3 py-2 text-[12px] font-medium text-[#dde4f0] hover:bg-accent/10 hover:text-accent transition-colors"
              >
                <FolderPlus size={13} /> Create Folder
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
