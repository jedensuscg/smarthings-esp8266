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
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Sensor"
        capability "Refresh"
        capability "Polling"

    }

    tiles {
        valueTile("temperature", "device.temperature", width: 2, height: 2) {
            tileAttribute("device.temperature", key: "TEMPERATURE")
            state "temperature", label:'Temperature\n ${currentValue}°', unit:"F",
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
            state "heatindex", label:'Heat Index\n ${currentValue}°', unit:"F",
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

        main "temperature"


        details(["humidity", "temperature", "heatindex"])
    }

    simulator {
        status "Temperature 72.0":"simulator:true, temp:72.00"
        status "humidity 68.0":"simulator:true, humidity:68.0"

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

//poll 
def poll() {
    LOG("poll()")
    return refresh()
}

// refresh
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

    if (temps.containsKey("heatindex")) {
        def ev = [
            name:   "heatindex",
            value:  scaleTemperature(temps.heatindex.toFloat()),
            unit:   getTemperatureScale(),
        ]

        events << createEvent(ev)
    }

    LOG("events: ${events}")
    return events
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
    return "Version .9 (01/29/2022)"
}

private def textCopyright() {
    return "Copyright (c) 2022 James Edens"
}

private def LOG(message) {
    //log.trace message
}

private def STATE() {
    log.trace "deviceNetworkId: ${device.deviceNetworkId}"
    log.trace "temperature: ${device.currentValue("temperature")}"
    log.trace "heatindex: ${device.currentValue("heatindex")}"
    log.trace "humidity: ${device.currentValue("humidity")}"

}