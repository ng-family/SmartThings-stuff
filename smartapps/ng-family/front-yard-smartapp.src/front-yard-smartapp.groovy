/**
 *  Front Yard SmartApp
 *
 *  Copyright 2017 Paul Ng
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * templated from Sunrise, Sunset
 */
definition(
    name: "Front Yard SmartApp",
    namespace: "ng-family",
    author: "Paul Ng",
    description: "Turn ON/OFF Front Yard switch based on mode, time or sun movement plus random offset",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/ModeMagic/rise-and-shine.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/ModeMagic/rise-and-shine@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/ModeMagic/rise-and-shine@2x.png")


preferences {
//    section ("Set for which mode(s)") {
//        input "enModes", "mode", title: "select a mode(s)", multiple: true
//    }
	section ("At sunrise...") {
		input "sunriseMode", "mode", title: "Change mode to?", required: false
		input "sunriseOn", "capability.switch", title: "Turn on?", required: false, multiple: true
		input "sunriseOff", "capability.switch", title: "Turn off?", required: false, multiple: true
	}
	section ("At sunset...") {
		input "sunsetMode", "mode", title: "Change mode to?", required: false
		input "sunsetOn", "capability.switch", title: "Turn on?", required: false, multiple: true
		input "sunsetOff", "capability.switch", title: "Turn off?", required: false, multiple: true
	}
	section ("Sunrise offset (optional)...") {
		input "sunriseOffsetValue", "text", title: "HH:MM", required: false
		input "sunriseOffsetDir", "enum", title: "Before or After", required: false, options: ["Before","After"]
	}
	section ("Sunset offset (optional)...") {
		input "sunsetOffsetValue", "text", title: "HH:MM", required: false
		input "sunsetOffsetDir", "enum", title: "Before or After", required: false, options: ["Before","After"]
	}
	section ("Zip code (optional, defaults to location coordinates)...") {
		input "zipCode", "text", required: false
	}
	section( "Notifications" ) {
        input("recipients", "contact", title: "Send notifications to") {
            input "sendPushMessage", "enum", title: "Send a push notification?", options: ["Yes", "No"], required: false
            input "phoneNumber", "phone", title: "Send a text message?", required: false
        }
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
	subscribe(location, "position", locationPositionChange)
	subscribe(location, "sunriseTime", sunriseSunsetTimeHandler)
	subscribe(location, "sunsetTime", sunriseSunsetTimeHandler)
	astroCheck()
}


def locationPositionChange(evt) {
	log.trace "locationChange()"
	astroCheck()
}

def sunriseSunsetTimeHandler(evt) {
	log.trace "sunriseSunsetTimeHandler()"
	astroCheck()
}

def astroCheck() {
	def s = getSunriseAndSunset(zipCode: zipCode, sunriseOffset: sunriseOffset, sunsetOffset: sunsetOffset)
	def now = new Date()
	def riseTime = s.sunrise
	def setTime = s.sunset
	log.debug "riseTime: $riseTime"
	log.debug "setTime: $setTime"
	if (state.riseTime != riseTime.time) {
		unschedule("sunriseHandler")
		if(riseTime.before(now)) {
			riseTime = riseTime.next()
		}
		state.riseTime = riseTime.time
		log.info "scheduling sunrise handler for $riseTime"
		schedule(riseTime, sunriseHandler)
	}
	if (state.setTime != setTime.time) {
		unschedule("sunsetHandler")
        
	    if(setTime.before(now)) {
		    setTime = setTime.next()
	    }
		state.setTime = setTime.time
		log.info "scheduling sunset handler for $setTime"
	    schedule(setTime, sunsetHandler)
	}
}
// TODO: implement event handlers

def sunriseHandler() {
	if (location.mode != "Dark") {
        log.info "Executing sunrise handler"
		if (sunriseOn) {
			sunriseOn.on()
		}
		if (sunriseOff) {
			sunriseOff.off()
		}
		changeMode(sunriseMode)		
    } else {
		log.info "Current $location.mode mode has overridden sunrise handler"
	}
}


def sunsetHandler() {
	if (location.mode != "Dark") {
	log.info "Executing sunset handler"
		if (sunsetOn) {
			sunsetOn.on()
		}
		if (sunsetOff) {
			sunsetOff.off()
		}
		changeMode(sunsetMode)
    } else {
		log.info "Current $location.mode mode has overridden sunrise handler"
	}
}

def changeMode(newMode) {
	if (newMode && location.mode != newMode) {
		if (location.modes?.find{it.name == newMode}) {
			setLocationMode(newMode)
			send "${label} has changed the mode to '${newMode}'"
		}
		else {
			send "${label} tried to change to undefined mode '${newMode}'"
		}
	}
}

private send(msg) {
    if (location.contactBookEnabled) {
        log.debug("sending notifications to: ${recipients?.size()}")
        sendNotificationToContacts(msg, recipients)
    }
    else {
        if (sendPushMessage != "No") {
            log.debug("sending push message")
            sendPush(msg)
        }
        if (phoneNumber) {
            log.debug("sending text message")
            sendSms(phoneNumber, msg)
        }
    }
	log.debug msg
}



private getLabel() {
	app.label ?: "SmartThings"
}

private getSunriseOffset() {
	sunriseOffsetValue ? (sunriseOffsetDir == "Before" ? "-$sunriseOffsetValue" : sunriseOffsetValue) : null
}

private getSunsetOffset() {
	sunsetOffsetValue ? (sunsetOffsetDir == "Before" ? "-$sunsetOffsetValue" : sunsetOffsetValue) : null
}