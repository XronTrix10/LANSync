import type { Device } from "../types";

const STORAGE_KEY = "lansync_recent_devices";

/** Loads recent devices from localStorage. */
export function loadRecentDevices(): Device[] {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved ? JSON.parse(saved) : [];
  } catch {
    return [];
  }
}

/** Adds a device to the recent list (deduplicates by DeviceID), persists to localStorage. */
export function pushToRecentDevices(prev: Device[], device: Device): Device[] {
  const filtered = prev.filter((d) => {
    // 1. If both have DeviceIDs, compare them strictly.
    if (d.deviceId && device.deviceId) {
      return d.deviceId !== device.deviceId;
    }
    // 2. Fallback for clearing out old pre-update legacy data.
    return d.ip !== device.ip && d.deviceName !== device.deviceName;
  });

  const updated = [device, ...filtered].slice(0, 5); // Keep last 5
  localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
  return updated;
}

/** Removes a device from the recent list by DeviceID, persists to localStorage. */
export function removeFromRecentDevices(
  prev: Device[],
  deviceId: string,
): Device[] {
  // We use deviceId, but allow IP as a fallback in case legacy UI still passes IP
  const updated = prev.filter(
    (d) => d.deviceId !== deviceId && d.ip !== deviceId,
  );
  localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
  return updated;
}

/** Toggles the autoConnect flag for a specific device, persists to localStorage. */
export function toggleAutoConnect(
  prev: Device[],
  deviceId: string,
  autoConnect: boolean,
): Device[] {
  const updated = prev.map((d) =>
    d.deviceId === deviceId ? { ...d, autoConnect } : d,
  );
  localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
  return updated;
}
