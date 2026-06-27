import { useCallback, useState } from "react";
import {
  AcceptConnection,
  IdentifyDevice,
  RejectConnection,
  RequestConnection,
} from "../../wailsjs/go/main/App";
import type { ConnectionRequest, Device } from "../types";
import {
  pushToRecentDevices,
  removeFromRecentDevices,
} from "../utils/deviceUtils";
import { sendOSNotification } from "../utils/notificationUtils";
import { autoHandledSession } from "./useAutoConnect"; // ── Import Session Lock ──

type ShowToast = (
  message: string,
  type: "success" | "error",
  path?: string,
) => void;

export function useDeviceConnection(showToast: ShowToast) {
  const [devices, setDevices] = useState<Device[]>([]);
  const [activeDeviceIP, setActiveDeviceIP] = useState<string | null>(null);
  const [newDeviceIP, setNewDeviceIP] = useState<string>("");
  const [discoveredDevices, setDiscoveredDevices] = useState<any[]>([]);
  const [pendingRequest, setPendingRequest] =
    useState<ConnectionRequest | null>(null);
  const [recentDevices, setRecentDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(false);

  const addRecentDevice = useCallback((device: Device) => {
    setRecentDevices((prev) => pushToRecentDevices(prev, device));
  }, []);

  const removeRecentDevice = useCallback((ip: string) => {
    setRecentDevices((prev) => removeFromRecentDevices(prev, ip));
  }, []);

  const connectToDevice = useCallback(
    async (ipToConnect: string = newDeviceIP) => {
      if (!ipToConnect) return;
      setLoading(true);
      try {
        const device: any = await IdentifyDevice(ipToConnect);
        showToast(
          `Asking ${device.deviceName || ipToConnect} to connect...`,
          "success",
        );

        const response = await RequestConnection(device.ip, device.port);

        if (response && response.accepted) {
          device.deviceName = response.deviceName;
          device.deviceId = response.deviceId;

          setDevices((prev) => {
            if (prev.some((d) => d.ip === device.ip)) return prev;
            return [...prev, device];
          });
          setActiveDeviceIP(device.ip);
          setNewDeviceIP("");
          addRecentDevice(device);
          showToast(
            `Connection established with ${response.deviceName}!`,
            "success",
          );
        } else {
          showToast("Connection was declined", "error");
        }
      } catch (err: any) {
        showToast(err.message || String(err), "error");
      } finally {
        setLoading(false);
      }
    },
    [newDeviceIP, showToast, addRecentDevice],
  );

  const handleAcceptConnection = useCallback(() => {
    if (!pendingRequest) return;
    AcceptConnection(pendingRequest.ip);
    const newDevice: Device = {
      ip: pendingRequest.ip,
      port: pendingRequest.port,
      deviceName: pendingRequest.deviceName,
      os: pendingRequest.os,
      type: pendingRequest.type,
      deviceId: pendingRequest.deviceId,
      autoConnect: pendingRequest.autoConnect,
    };
    setDevices((prev) => [...prev, newDevice]);
    setActiveDeviceIP(newDevice.ip);
    addRecentDevice(newDevice);
    setPendingRequest(null);
    showToast(`Connected securely to ${newDevice.deviceName}`, "success");
  }, [pendingRequest, showToast, addRecentDevice]);

  const handleRejectConnection = useCallback(() => {
    if (!pendingRequest) return;
    RejectConnection(pendingRequest.ip);
    setPendingRequest(null);
  }, [pendingRequest]);

  const onConnectionRequested = useCallback(
    (req: ConnectionRequest) => {
      // const recentList = pushToRecentDevices([], req); // Extract without local storage util direct
      const savedString = localStorage.getItem("lansync_recent_devices");
      const savedDevices: Device[] = savedString ? JSON.parse(savedString) : [];
      const savedDevice = savedDevices.find((d) => d.deviceId === req.deviceId);

      // ── INBOUND SESSION CHECK ──
      if (
        savedDevice &&
        savedDevice.autoConnect &&
        !autoHandledSession.has(req.deviceId)
      ) {
        autoHandledSession.add(req.deviceId);
        AcceptConnection(req.ip);

        const newDevice: Device = {
          ...req,
          autoConnect: savedDevice.autoConnect,
        };
        setDevices((prev) => {
          if (prev.some((d) => d.ip === newDevice.ip)) return prev;
          return [...prev, newDevice];
        });
        setActiveDeviceIP(newDevice.ip);
        addRecentDevice(newDevice);
        showToast(`Auto-connected to ${newDevice.deviceName}`, "success");
      } else {
        // Fallback to manual if auto is off OR we already auto-connected this session
        setPendingRequest(req);
        sendOSNotification(
          "Connection Request",
          `${req.deviceName} wants to connect.`,
        );
      }
    },
    [addRecentDevice, showToast],
  );

  const onConnectionLost = useCallback(
    (ip: string) => {
      setDevices((prev) => prev.filter((d) => d.ip !== ip));
      setActiveDeviceIP((current) => (current === ip ? null : current));
      showToast("Device got disconnected", "error");
    },
    [showToast],
  );

  return {
    devices,
    setDevices,
    activeDeviceIP,
    setActiveDeviceIP,
    newDeviceIP,
    setNewDeviceIP,
    pendingRequest,
    setPendingRequest,
    recentDevices,
    setRecentDevices,
    discoveredDevices,
    setDiscoveredDevices,
    loading,
    connectToDevice,
    addRecentDevice,
    removeRecentDevice,
    handleAcceptConnection,
    handleRejectConnection,
    onConnectionRequested,
    onConnectionLost,
  };
}
