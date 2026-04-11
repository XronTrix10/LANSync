package sys

import (
	"net"
	"strings"
)

// GetLocalIPs finds up to 3 active local network IPv4 addresses
func GetLocalIPs() []string {
	var validIPs []string
	ifaces, err := net.Interfaces()
	if err != nil {
		return []string{}
	}

	for _, iface := range ifaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, _ := iface.Addrs()
		for _, addr := range addrs {
			var ip net.IP
			switch v := addr.(type) {
			case *net.IPNet:
				ip = v.IP
			case *net.IPAddr:
				ip = v.IP
			}
			if ip4 := ip.To4(); ip4 != nil && !ip4.IsLoopback() {
				ipStr := ip4.String()

				// Ensure it's a private LAN IP block
				if strings.HasPrefix(ipStr, "10.") || strings.HasPrefix(ipStr, "172.") || strings.HasPrefix(ipStr, "192.168.") {
					validIPs = append(validIPs, ipStr)
					if len(validIPs) == 3 {
						return validIPs
					}
				}
			}
		}
	}

	if len(validIPs) == 0 {
		return []string{}
	}
	return validIPs
}
