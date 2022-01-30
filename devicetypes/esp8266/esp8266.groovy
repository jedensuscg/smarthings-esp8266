/**
 *  Filtrete 3M-50 WiFi Thermostat.
 *
 *  For more information, please visit:
 *  <https://github.com/statusbits/smartthings/tree/master/RadioThermostat/>
 *
 *  --------------------------------------------------------------------------
 *
 *  Copyright (c) 2014 Statusbits.com
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation, either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  --------------------------------------------------------------------------
 *
 *  Version 1.0.3 (07/20/2015)
 */
 
 /**Derived from the above by Bigfoot1970, Copyright 2016 Version 1.0 released under the GPL */

import groovy.json.JsonSlurper

preferences {
    input("confIpAddr", "string", title:"DHT22 IP Address",
        required:true, displayDuringSetup: true)
    input("confTcpPort", "number", title:"DHT22 TCP Port",
        required:true, displayDuringSetup:true)
}

metadata {
    definition (name:"dht22 test", namespace:"jedensuscg", author:"jedensuscg@gmail.com") {
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Sensor"
        capability "Refresh"
        capability "Polling"

    }

    tiles {
        valueTile("temperature", "device.temperature") {
            state "temperature", label:'${currentValue}°', unit:"F",
                backgroundColors:[
                    [value: 31, color: "#153591"],
                    [value: 44, color: "#1e9cbb"],
                    [value: 59, color: "#90d2a7"],
                    [value: 74, color: "#44b621"],
                    [value: 84, color: "#f1d801"],
                    [value: 95, color: "#d04e00"],
                    [value: 96, color: "#bc2323"]
                ]
        }

        valueTile("heatindex", "device.heatindex", inactiveLabel:false) {
            state "default", label:'HI ${currentValue}°', unit:"F",
                backgroundColors:[
                    [value: 31, color: "#153591"],
                    [value: 44, color: "#1e9cbb"],
                    [value: 59, color: "#90d2a7"],
                    [value: 74, color: "#44b621"],
                    [value: 84, color: "#f1d801"],
                    [value: 95, color: "#d04e00"],
                    [value: 96, color: "#bc2323"]
                ]
        }

        valueTile("humidity", "device.humidity", inactiveLabel:false) {
            state "default", label:'${currentValue}%', unit:"%",
                backgroundColors:[
                    [value: 31, color: "#153591"],
                    [value: 44, color: "#1e9cbb"],
                    [value: 59, color: "#90d2a7"],
                    [value: 74, color: "#44b621"],
                    [value: 84, color: "#f1d801"],
                    [value: 95, color: "#d04e00"],
                    [value: 96, color: "#bc2323"]
                ]
        }

        main(["temperature"])

        details(["humidity", "temperature", "heatindex"])
    }

    simulator {
        status "Temperature 72.0":      "simulator:true, temp:72.00"
        status "Cooling Setpoint 76.0": "simulator:true, t_cool:76.00"
        status "Heating Setpoint 68.0": "simulator:true, t_cool:68.00"
        status "Thermostat Mode Off":   "simulator:true, tmode:0"
        status "Thermostat Mode Heat":  "simulator:true, tmode:1"
        status "Thermostat Mode Cool":  "simulator:true, tmode:2"
        status "Thermostat Mode Auto":  "simulator:true, tmode:3"
        status "Fan Mode Auto":         "simulator:true, fmode:0"
        status "Fan Mode Circulate":    "simulator:true, fmode:1"
        status "Fan Mode On":           "simulator:true, fmode:2"
        status "State Off":             "simulator:true, tstate:0"
        status "State Heat":            "simulator:true, tstate:1"
        status "State Cool":            "simulator:true, tstate:2"
        status "Fan State Off":         "simulator:true, fstate:0"
        status "Fan State On":          "simulator:true, fstate:1"
        status "Hold Disabled":         "simulator:true, hold:0"
        status "Hold Enabled":          "simulator:true, hold:1"
    }
}

def updated() {
    log.info "DHT22 Sensor. ${textVersion()}. ${textCopyright()}"
	LOG("$device.displayName updated with settings: ${settings.inspect()}")

    state.hostAddress = "${settings.confIpAddr}:${settings.confTcpPort}"
    state.dni = createDNI(settings.confIpAddr, settings.confTcpPort)

    STATE()
}



def parse(String message) {
    LOG("parse(${message})")

    def msg = stringToMap(message)

    if (msg.headers) {
        // parse HTTP response headers
        def headers = new String(msg.headers.decodeBase64())
        def parsedHeaders = parseHttpHeaders(headers)
        LOG("parsedHeaders: ${parsedHeaders}")
        if (parsedHeaders.status != 200) {
            log.error "Server error: ${parsedHeaders.reason}"
            return null
        }

        // parse HTTP response body
        if (!msg.body) {
            log.error "HTTP response has no body"
            return null
        }

        def body = new String(msg.body.decodeBase64())
        def slurper = new JsonSlurper()
        def temps = slurper.parseText(body)

        return parseTstatData(temps)
    } else if (msg.containsKey("simulator")) {
        // simulator input
        return parseTstatData(msg)
    }

    return null
}

// polling.poll 
def poll() {
    LOG("poll()")
    return refresh()
}

// refresh.refresh
def refresh() {
    LOG("refresh()")
    //STATE()
    return apiGet("/temp")
}

// Creates Device Network ID in 'AAAAAAAA:PPPP' format
private String createDNI(ipaddr, port) { 
    LOG("createDNI(${ipaddr}, ${port})")

    def hexIp = ipaddr.tokenize('.').collect {
        String.format('%02X', it.toInteger())
    }.join()

    def hexPort = String.format('%04X', port.toInteger())

    return "${hexIp}:${hexPort}"
}

private updateDNI() { 
    if (device.deviceNetworkId != state.dni) {
        device.deviceNetworkId = state.dni
    }
}

private apiGet(String path) {
    LOG("apiGet(${path})")

    def headers = [
        HOST:       state.hostAddress,
        Accept:     "*/*"
    ]

    def httpRequest = [
        method:     'GET',
        path:       path,
        headers:    headers
    ]

    updateDNI()

    return new physicalgraph.device.HubAction(httpRequest)
}

private apiPost(String path, data) {
    LOG("apiPost(${path}, ${data})")

    def headers = [
        HOST:       state.hostAddress,
        Accept:     "*/*"
    ]

    def httpRequest = [
        method:     'POST',
        path:       path,
        headers:    headers,
        body:       data
    ]

    updateDNI()

    return new physicalgraph.device.HubAction(httpRequest)
}

private def writeTempValue(name, value) {
    LOG("writeTempValue(${name}, ${value})")

    def json = "{\"${name}\": ${value}}"
    def hubActions = [
        apiPost("/temp", json),
        delayHubAction(4000),
        apiGet("/temp")
    ]

    return hubActions
}

private def delayHubAction(ms) {
    return new physicalgraph.device.HubAction("delay ${ms}")
}

private parseHttpHeaders(String headers) {
    def lines = headers.readLines()
    def status = lines.remove(0).split()

    def result = [
        protocol:   status[0],
        status:     status[1].toInteger(),
        reason:     status[2]
    ]

    return result
}

private def parseTstatData(Map temps) {
    LOG("parseTstatData(${temps})")

    def events = []
    if (temps.containsKey("error_msg")) {
        log.error "Thermostat error: ${temps.error_msg}"
        return null
    }

    if (temps.containsKey("success")) {
        // this is POST response - ignore
        return null
    }

    if (temps.containsKey("temp")) {
        //Float temp = temps.temp.toFloat()
        def ev = [
            name:   "temperature",
            value:  scaleTemperature(temps.temp.toFloat()),
            unit:   getTemperatureScale(),
        ]

        events << createEvent(ev)
    }

    if (temps.containsKey("humidity")) {
        def ev = [
            name:   "humidity",
            value:  temps.humidity.toFloat(),
            unit:   "%",
        ]

        events << createEvent(ev)
    }

    if (temps.containsKey("heat_index")) {
        def ev = [
            name:   "heatindex",
            value:  scaleTemperature(temps.heat_index.toFloat()),
            unit:   getTemperatureScale(),
        ]

        events << createEvent(ev)
    }

    LOG("events: ${events}")
    return events
}

private def parseThermostatState(val) {
    def values = [
        "idle",     // 0
        "heating",  // 1
        "cooling"   // 2
    ]

    return values[val.toInteger()]
}

private def parseFanState(val) {
    def values = [
        "off",      // 0
        "on"        // 1
    ]

    return values[val.toInteger()]
}

private def parseThermostatMode(val) {
    def values = [
        "off",      // 0
        "heat",     // 1
        "cool",     // 2
        "auto"      // 3
    ]

    return values[val.toInteger()]
}

private def parseFanMode(val) {
    def values = [
        "auto",     // 0
        "circulate",// 1 (not supported by CT30)
        "on"        // 2
    ]

    return values[val.toInteger()]
}

private def parseThermostatHold(val) {
    def values = [
        "off",      // 0
        "on"        // 1
    ]

    return values[val.toInteger()]
}

private def scaleTemperature(Float temp) {
    if (getTemperatureScale() == "C") {
        return temperatureFtoC(temp)
    }

    return temp.round(1)
}

private def temperatureCtoF(Float tempC) {
    Float t = (tempC * 1.8) + 32
    return t.round(1)
}

private def temperatureFtoC(Float tempF) {
    Float t = (tempF - 32) / 1.8
    return t.round(1)
}

private def textVersion() {
    return "Version 1.0.3 (08/25/2015)"
}

private def textCopyright() {
    return "Copyright (c) 2014 Statusbits.com"
}

private def LOG(message) {
    //log.trace message
}

private def STATE() {
    log.trace "deviceNetworkId: ${device.deviceNetworkId}"
    log.trace "temperature: ${device.currentValue("temperature")}"
    log.trace "heat_index: ${device.currentValue("heat_index")}"
    log.trace "humidity: ${device.currentValue("humidity")}"

}