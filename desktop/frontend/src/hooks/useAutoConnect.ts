import { useEffect, useState, useRef } from "react";
import {
  GetDeviceID,
  AcceptConnection,
  RequestConnection,
} from "../../wailsjs/go/main/App";
import { EventsOn } from "../../wailsjs/runtime/runtime";
import type { ConnectionRequest, DiscoveredDevice } from "../types";
import { loadRecentDevices } from "../utils/deviceUtils";

export function useAutoConnect(
  availableDevices: DiscoveredDevice[], // ── Passed in from main App state
  onConnected: (device: DiscoveredDevice) => void,
) {
  const [localId, setLocalId] = useState<string>("");
  const connectingTo = useRef<Set<string>>(new Set());

  // 1. Fetch Local Device ID on mount
  useEffect(() => {
    GetDeviceID().then(setLocalId);
  }, []);

  // 2. OUTGOING: Watch the available devices state
  useEffect(() => {
    if (!localId || availableDevices.length === 0) return;

    // Load preferences once per update
    const recentList = loadRecentDevices();

    // Loop through the devices we can currently see on the network
    availableDevices.forEach((availableDevice) => {
      // Check if this specific visible device is in our recent list with autoConnect: true
      const savedDevice = recentList.find(
        (d) => d.deviceId === availableDevice.deviceId,
      );

      if (savedDevice && savedDevice.autoConnect) {
        // ── LEADER ELECTION TIE-BREAKER ──
        if (localId > availableDevice.deviceId) {
          if (!connectingTo.current.has(availableDevice.deviceId)) {
            connectingTo.current.add(availableDevice.deviceId);

            RequestConnection(availableDevice.ip, "34931")
              .then((resp) => {
                if (resp.accepted) {
                  onConnected(availableDevice);
                }
              })
              .catch((err) => console.error("Auto-connect failed", err))
              .finally(() => {
                connectingTo.current.delete(availableDevice.deviceId);
              });
          }
        }
      }
    });
  }, [availableDevices, localId, onConnected]); // Re-runs whenever a new device becomes available!

  // 3. INBOUND: Handle incoming requests (Auto-Accept)
  useEffect(() => {
    const unsubscribe = EventsOn(
      "connection_requested",
      (req: ConnectionRequest) => {
        const recentList = loadRecentDevices();
        const savedDevice = recentList.find((d) => d.deviceId === req.deviceId);

        if (savedDevice && savedDevice.autoConnect) {
          console.log(`Auto-accepting connection from ${req.deviceName}`);
          AcceptConnection(req.ip);

          onConnected({
            deviceId: req.deviceId,
            ip: req.ip,
            deviceName: req.deviceName,
            os: req.os,
          });
        } else {
          // Trigger manual accept modal
        }
      },
    );

    return () => unsubscribe();
  }, [onConnected]);
}
