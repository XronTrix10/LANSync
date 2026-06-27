import { useEffect, useRef, useState } from "react";
import {
  CancelTransfers,
  DisableAutoConnect,
  Disconnect,
  GetDeviceName,
  GetHomeDir,
  GetLocalIPs,
  GetSharedDir,
  RequestAutoConnect,
  ResolveAutoConnect,
  SaveDeviceName,
  SaveSharedDir,
} from "../wailsjs/go/main/App";
import { Environment, EventsOff, EventsOn } from "../wailsjs/runtime/runtime";

import { FileBrowser } from "./components/FileBrowser";
import { Sidebar } from "./components/Sidebar";
import { TitleBar } from "./components/TitleBar";
import { ToastContainer } from "./components/ToastContainer";
import { TransferDrawer } from "./components/TransferDrawer";

import { useAutoConnect } from "./hooks/useAutoConnect";
import { useDeviceConnection } from "./hooks/useDeviceConnection";
import { useFileTransfer } from "./hooks/useFileTransfer";
import { useToasts } from "./hooks/useToasts";

import { AutoConnectPromptModal } from "./components/modals/AutoConnectPromptModal";
import { ConnectionRequestModal } from "./components/modals/ConnectionRequestModal";
import { SettingsModal } from "./components/modals/SettingsModal";
import type { Device, DiscoveredDevice, TransferProgress } from "./types";
import { loadRecentDevices, toggleAutoConnect } from "./utils/deviceUtils";

export default function App() {
  const [os, setOs] = useState("");
  const [localIPs, setLocalIPs] = useState<string[]>(["Loading..."]);
  const [activeTransfers, setActiveTransfers] = useState<
    Record<string, TransferProgress>
  >({});
  const [showSettings, setShowSettings] = useState(false);
  const [deviceName, setDeviceName] = useState("");
  const [sharedDir, setSharedDir] = useState("");
  const [homeDir, setHomeDir] = useState("");
  const [uploading, setUploading] = useState(false);
  const [incomingAutoConnectReq, setIncomingAutoConnectReq] =
    useState<any>(null);

  const activeDeviceIPRef = useRef<string | null>(null);
  const currentPathRef = useRef<string>("/");
  const osRef = useRef<string>("");

  const { toasts, showToast } = useToasts();

  const {
    devices,
    setDevices,
    activeDeviceIP,
    setActiveDeviceIP,
    newDeviceIP,
    setNewDeviceIP,
    pendingRequest,
    recentDevices,
    setRecentDevices,
    discoveredDevices,
    setDiscoveredDevices,
    loading: connectionLoading,
    connectToDevice,
    removeRecentDevice,
    handleAcceptConnection,
    handleRejectConnection,
    onConnectionRequested,
    onConnectionLost,
  } = useDeviceConnection(showToast);

  const {
    currentPath,
    setCurrentPath,
    parentPath,
    files,
    setFiles,
    loading: fileLoading,
    navigateTo,
    downloadItem,
    handleUploadFiles,
    handleUploadFolder,
    handleCreateFolder,
    handleHtmlDropUpload,
    handleShareClipboard,
    handleNativeFileDrop,
  } = useFileTransfer(
    devices,
    activeDeviceIPRef,
    currentPathRef,
    osRef,
    showToast,
  );

  const loading = connectionLoading || fileLoading;

  useAutoConnect(discoveredDevices, (device) => {
    // ── Silently add the auto-connected device to the sidebar ──
    setDevices((prev) => {
      if (prev.some((d) => d.ip === device.ip)) return prev;
      return [...prev, { ...device, port: "34931", type: "desktop" } as Device];
    });
    setActiveDeviceIP(device.ip);
    showToast(`Auto-connected to ${device.deviceName}`, "success");
  });

  const handleToggleAutoConnect = async (
    ip: string,
    deviceId: string,
    enable: boolean,
  ) => {
    if (enable) {
      showToast("Asking for Auto-Connect...", "success");
      try {
        await RequestAutoConnect(ip);
        const updated = toggleAutoConnect(recentDevices, deviceId, true);
        setRecentDevices(updated);
        showToast("Auto-Connect Enabled!", "success");
      } catch (err) {
        showToast("Auto-Connect request was rejected or timed out.", "error");
      }
    } else {
      showToast("Disabling Auto-Connect...", "success");
      try {
        await DisableAutoConnect(ip);
        const updated = toggleAutoConnect(recentDevices, deviceId, false);
        setRecentDevices(updated);
        showToast("Auto-Connect Disabled.", "success");
      } catch (err) {
        showToast("Failed to communicate with remote device.", "error");
      }
    }
  };

  useEffect(() => {
    activeDeviceIPRef.current = activeDeviceIP;
  }, [activeDeviceIP]);

  useEffect(() => {
    currentPathRef.current = currentPath;
  }, [currentPath]);

  useEffect(() => {
    osRef.current = os;
  }, [os]);

  useEffect(() => {
    if (activeDeviceIP) {
      navigateTo("/", activeDeviceIP);
    } else {
      setFiles([]);
      setCurrentPath("/");
    }
  }, [activeDeviceIP, navigateTo, setFiles, setCurrentPath]);

  useEffect(() => {
    setRecentDevices(loadRecentDevices());

    Environment().then((env) => setOs(env.platform));
    GetLocalIPs().then(setLocalIPs);
    GetDeviceName().then(setDeviceName);
    GetHomeDir().then(setHomeDir);
    GetSharedDir().then(setSharedDir);

    const networkPoll = setInterval(
      () => GetLocalIPs().then(setLocalIPs),
      3000,
    );

    EventsOn("transfer_progress", (progress: TransferProgress) => {
      setActiveTransfers((prev) => ({ ...prev, [progress.id]: progress }));
    });
    EventsOn("transfer_complete", (id: string) => {
      setActiveTransfers((prev) => {
        const next = { ...prev };
        delete next[id];
        return next;
      });
    });
    EventsOn("upload_start", () => setUploading(true));
    EventsOn("upload_complete", () => setUploading(false));
    EventsOn("devices_discovered", (devs: DiscoveredDevice[]) => {
      setDiscoveredDevices(devs || []);
    });
    EventsOn("connection_requested", onConnectionRequested);
    EventsOn("autoconnect_requested", (req) => {
      setIncomingAutoConnectReq(req);
    });

    // ── INCOMING DISABLE EVENT ──
    EventsOn("autoconnect_disabled", (req) => {
      setRecentDevices((prev) => toggleAutoConnect(prev, req.deviceId, false));
      showToast(`Auto-Connect was disabled by ${req.deviceName}`, "success");
    });

    EventsOn("connection_lost", onConnectionLost);
    EventsOn(
      "wails:file-drop",
      async (_x: number, _y: number, paths: string[]) => {
        handleNativeFileDrop(paths);
      },
    );

    if ("Notification" in window && Notification.permission === "default") {
      Notification.requestPermission();
    }

    return () => {
      clearInterval(networkPoll);
      EventsOff("transfer_progress");
      EventsOff("transfer_complete");
      EventsOff("upload_start");
      EventsOff("upload_complete");
      EventsOff("devices_discovered");
      EventsOff("connection_requested");
      EventsOff("connection_lost");
      EventsOff("wails:file-drop");
      EventsOff("autoconnect_requested");
      EventsOff("autoconnect_disabled");
    };
  }, [
    onConnectionRequested,
    onConnectionLost,
    handleNativeFileDrop,
    setRecentDevices,
  ]);

  const handleSaveSettings = async () => {
    try {
      await SaveDeviceName(deviceName);
      await SaveSharedDir(sharedDir);
      setShowSettings(false);
      showToast("Settings saved", "success");
    } catch {
      showToast("Failed to save settings", "error");
    }
  };

  const handleResolveAutoConnect = (accept: boolean) => {
    if (!incomingAutoConnectReq) return;
    ResolveAutoConnect(incomingAutoConnectReq.ip, accept);

    if (accept) {
      const updated = toggleAutoConnect(
        recentDevices,
        incomingAutoConnectReq.deviceId,
        true,
      );
      setRecentDevices(updated);
      showToast(
        `Auto-Connect enabled for ${incomingAutoConnectReq.deviceName}`,
        "success",
      );
    }
    setIncomingAutoConnectReq(null);
  };

  return (
    <div className="flex flex-col h-screen bg-bg-base text-text select-none overflow-hidden">
      <ToastContainer toasts={toasts} />
      <TitleBar />

      <ConnectionRequestModal
        request={pendingRequest}
        onAccept={handleAcceptConnection}
        onReject={handleRejectConnection}
      />

      <AutoConnectPromptModal
        device={incomingAutoConnectReq}
        onEnable={() => handleResolveAutoConnect(true)}
        onSkip={() => handleResolveAutoConnect(false)}
      />

      <div className="flex flex-1 overflow-hidden min-h-0">
        <Sidebar
          localDeviceName={deviceName}
          localIPs={localIPs}
          devices={devices}
          activeDeviceIP={activeDeviceIP}
          recentDevices={recentDevices}
          discoveredDevices={discoveredDevices}
          newDeviceIP={newDeviceIP}
          loading={connectionLoading}
          onSetActiveDevice={setActiveDeviceIP}
          onDisconnect={(ip) => Disconnect(ip)}
          onToggleAutoConnect={handleToggleAutoConnect}
          onNewDeviceIPChange={setNewDeviceIP}
          onConnect={connectToDevice}
          onRemoveRecent={removeRecentDevice}
          setShowSettings={setShowSettings}
          onRefresh={() => {
            GetLocalIPs().then(setLocalIPs);
            showToast("Network refreshed", "success");
          }}
        />

        <div className="flex-1 flex flex-col min-w-0 overflow-hidden m-2 mt-0 rounded-xl">
          <FileBrowser
            os={os}
            activeDeviceIP={activeDeviceIP}
            files={files}
            currentPath={currentPath}
            parentPath={parentPath}
            deviceRootPath={""}
            loading={loading}
            uploading={uploading}
            onNavigate={(path) => navigateTo(path)}
            onDownload={downloadItem}
            onUploadFiles={handleUploadFiles}
            onUploadFolder={handleUploadFolder}
            onHtmlDropUpload={handleHtmlDropUpload}
            onCreateFolder={handleCreateFolder}
            onShareClipboard={handleShareClipboard}
            onError={(msg) => showToast(msg, "error")}
          />
          <TransferDrawer
            transfers={activeTransfers}
            onCancelAll={() => {
              if (activeDeviceIP) CancelTransfers(activeDeviceIP);
            }}
          />
        </div>
      </div>

      <SettingsModal
        isOpen={showSettings}
        deviceName={deviceName}
        sharedDir={sharedDir}
        homeDir={homeDir}
        setDeviceName={setDeviceName}
        setSharedDir={setSharedDir}
        onClose={() => setShowSettings(false)}
        onSave={handleSaveSettings}
      />
    </div>
  );
}
