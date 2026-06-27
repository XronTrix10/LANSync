import { useEffect, useState } from "react";
import { GetDeviceID, RequestConnection } from "../../wailsjs/go/main/App";
import type { DiscoveredDevice } from "../types";
import { loadRecentDevices } from "../utils/deviceUtils";

// ── GLOBAL SESSION MEMORY LOCK ──
// Stores devices we've already auto-connected to (or auto-accepted) this session.
// It never clears until the app restarts.
export const autoHandledSession = new Set<string>();

export function useAutoConnect(
  availableDevices: DiscoveredDevice[],
  onConnected: (device: DiscoveredDevice) => void,
) {
  const [localId, setLocalId] = useState<string>("");

  useEffect(() => {
    GetDeviceID().then(setLocalId);
  }, []);

  useEffect(() => {
    if (!localId || availableDevices.length === 0) return;

    const recentList = loadRecentDevices();

    availableDevices.forEach((availableDevice) => {
      const savedDevice = recentList.find(
        (d) => d.deviceId === availableDevice.deviceId,
      );

      if (savedDevice && savedDevice.autoConnect) {
        if (localId > availableDevice.deviceId) {
          // ── CHECK SESSION LOCK ──
          if (!autoHandledSession.has(availableDevice.deviceId)) {
            // Lock it permanently for this session
            autoHandledSession.add(availableDevice.deviceId);

            RequestConnection(availableDevice.ip, "34931")
              .then((resp) => {
                if (resp.accepted) {
                  onConnected(availableDevice);
                }
              })
              .catch((err) => console.error("Auto-connect failed", err));
          }
        }
      }
    });
  }, [availableDevices, localId, onConnected]);
}
