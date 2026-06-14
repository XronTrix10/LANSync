export namespace models {
	
	export class ConnectionResponse {
	    accepted: boolean;
	    tokenForA: string;
	    deviceName: string;
	    deviceId: string;
	
	    static createFrom(source: any = {}) {
	        return new ConnectionResponse(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.accepted = source["accepted"];
	        this.tokenForA = source["tokenForA"];
	        this.deviceName = source["deviceName"];
	        this.deviceId = source["deviceId"];
	    }
	}

}

