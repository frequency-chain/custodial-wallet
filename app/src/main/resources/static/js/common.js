const PayloadType = Object.freeze({
    CANCEL: Symbol("cancel"),
    START_SMS_PROCESS: Symbol("startSmsProcess"),
    LOGIN_PAYLOAD: Symbol("loginPayload"),
    SIGNUP_PAYLOAD: Symbol("signupPayload"),
    ONBOARD_PAYLOAD: Symbol("onboardPayload"),
    SMS_PAYLOAD_V2: Symbol("smsPayloadV2"),
    SMS_PAYLOAD_V3: Symbol("smsPayloadV3"),
    SMS_CODE_CALLBACK: Symbol("smsCodePayload"),
    ERROR: Symbol("error")
})

const SmsCallbackType = Object.freeze({
  SMS_CALLBACK_V1: Symbol("signedPayloadCallback"),
  SMS_CALLBACK_V2: Symbol("signedPayloadCallbackV2"),
  SMS_CALLBACK_V3: Symbol("signedPayloadCallbackV3"),
  SMS_CODE_CALLBACK: Symbol("smsCodePayloadCallback")
})

const errorIdsThatShouldNotCallBack = Object.freeze([18, 19]);

const amplicaProtocol = $(location).attr('protocol');
const amplicaHost = $(location).attr('host');
const amplicaServerAddress = `${amplicaProtocol}//${amplicaHost}`;

function isInIframe() {
    return window.parent.location !== window.location;
}

// function called when cancel button pressed.
// Contents of function provided by Webview holder
/** @namespace window.webkit
 *  @namespace window.webkit.messageHandlers.cancelTos
 *  @namespace window.webkit.messageHandlers.cancelTos
 **/
function cancel(targetOrigin, lastErrorIdSeen) {
  if (window.webkit !== undefined) {
    if (window.webkit.messageHandlers.cancelTos !== undefined) {
      window.webkit.messageHandlers.cancelTos.postMessage(true)
    }
  } else if (window.cancelTos !== undefined) {
    window.cancelTos.postMessage(true)
  } else if(errorIdsThatShouldNotCallBack.includes(lastErrorIdSeen)){
    // do nothing
  } else if(isInIframe()) {
    window.parent.postMessage({
      type: PayloadType.CANCEL.description
    }, targetOrigin)
  } else {
    alert("Cancel was called: This should callback to MeWe");
  }
}

function _startSmsProcess(sessionId, targetOrigin) {
  if (window.webkit !== undefined) {
    if (window.webkit.messageHandlers.startSmsProcess !== undefined) {
      window.webkit.messageHandlers.startSmsProcess.postMessage(sessionId)
    }
  } else if (window.startSmsProcess !== undefined) {
    window.startSmsProcess.postMessage(sessionId)
  } else if(isInIframe()) {
    window.parent.postMessage({
      type: PayloadType.START_SMS_PROCESS.description,
      payload: {
        sessionId: sessionId
      }
    }, targetOrigin)
  } else {
    alert(`startSmsProcess was called: This should callback to MeWe with sessionId=${sessionId}`);
    console.log(`startSmsProcess was called: This should callback to MeWe with sessionId=${sessionId}`)
  }
}

function _smsCallbackV2(payload, targetOrigin) {
  dispatchPayload(payload, PayloadType.SMS_PAYLOAD_V2, SmsCallbackType.SMS_CALLBACK_V2.description, targetOrigin, true);
}

function _smsCallbackV3(payload, targetOrigin) {
  dispatchPayload(payload, PayloadType.SMS_PAYLOAD_V3, SmsCallbackType.SMS_CALLBACK_V3.description, targetOrigin, true);
}

function smsCodeCallback(payload, targetOrigin) {
  dispatchPayload(payload, PayloadType.SMS_CODE_CALLBACK, SmsCallbackType.SMS_CODE_CALLBACK.description, targetOrigin, true);
}

function getTimeRemaining(endTime) {
  const t = Date.parse(endTime) - Date.parse(new Date());
  const seconds = Math.floor((t / 1000) % 60);
  const minutes = Math.floor((t / 1000 / 60) % 60);
  return {
    'total': t,
    'minutes': minutes,
    'seconds': seconds
  };
}

function initializeClock(id, endTime, resendCounter, resendLimit) {
  const clock = document.getElementById(id);
  const minutesSpan = clock.querySelector('.minutes');
  const secondsSpan = clock.querySelector('.seconds');

  const resend = $("#resendButton")
  if(resend) {
    resend.attr("disabled", "disabled")
    resend.addClass("disabled")
    resend.addClass("a-disabled")
  }

  function updateClock() {
    let t = getTimeRemaining(endTime);
    minutesSpan.innerHTML = (t.minutes.toString()).slice(-2);
    secondsSpan.innerHTML = ('0' + t.seconds).slice(-2);

    if (t.total <= 0) {
      clearInterval(timeInterval);
      minutesSpan.innerHTML = "0"
      secondsSpan.innerHTML = "00"
      if(resend && resendCounter < resendLimit) {
        resend.removeAttr("disabled")
        resend.removeClass("disabled")
        resend.removeClass("a-disabled")
      }
    }
  }

  updateClock();
  const timeInterval = setInterval(updateClock, 1000);
}

function apiPostCall(endpoint, payload, thenCallback, catchCallback = () => {}, displayErrorModal = true) {
  return $.ajax({
    url: `${amplicaServerAddress}/api/${endpoint}`,
    type: 'POST',
    data: JSON.stringify(payload),
    contentType: 'application/json; charset=utf-8'
  }).then(response => {
    if(thenCallback && typeof thenCallback === "function") thenCallback(response)
  }).catch((xhr, textStatus, error) => {
    if(catchCallback && typeof catchCallback === "function") catchCallback(xhr)
    if(displayErrorModal) {
      console.error(error);
      showErrorModal(xhr, messages);
      errorCallback(xhr.responseJSON, targetOrigin);
    }
    SENTRY.captureXhr(xhr, error);
  });
}

function showErrorModal(error, messages) {
  console.error("ERROR REPORT");
  console.error("ID: " + error.responseJSON.id);
  console.error("Description: " + error.responseJSON.description);
  console.error("Stacktrace: " + error.responseJSON.stackTrace);
  const [errorTitle, errorMessage] = ERRORS.getApiErrorMessages(error.responseJSON.id, messages)
  $("#error-title").text(errorTitle)
  $("#error-message").text(errorMessage)
  const elem = document.getElementById("errorModal");
  const instance = M.Modal.init(elem, {dismissible: false});
  instance.open();
}

function showGenericErrorModal(error, messages, errorId) {
  console.error("ERROR: " + error);
  const [errorTitle, errorMessage] = ERRORS.getApiErrorMessages(errorId === undefined ? -1 : parseInt(errorId), messages)
  $("#error-title").text(errorTitle)
  $("#error-message").text(errorMessage)
  const elem = document.getElementById("errorModal");
  const instance = M.Modal.init(elem, {dismissible: false});
  instance.open();
}

let commonLocalizedMessages = null;
const MESSAGES_URL = "/api/web/messages";
async function getUnescapedLocalizedMessages() {
  if (commonLocalizedMessages === null) {
    console.log(`Fetching messages from ${MESSAGES_URL}`);
    const messagesFetchRequest = await fetch(MESSAGES_URL);
    if (!messagesFetchRequest.ok) {
      console.error(`Couldn't load messages from ${MESSAGES_URL}`);
    } else {
      commonLocalizedMessages = await messagesFetchRequest.json();
    }
  }

  console.log("Returning commonLocalizedMessages");
  return commonLocalizedMessages;
}

function isIos(){
  return window.webkit !== undefined;
}

function errorCallback(error, targetOrigin) {
  dispatchPayload(error, PayloadType.ERROR, "errorCallbackHandler", targetOrigin, false);
}

function isFunctionListening(functionName) {
  let retVal = {
    isListening: false,
    isIFrame: false
  }
  if(isIos()) {
    const callback = window.webkit.messageHandlers[functionName];
    if(callback !== undefined) {
      retVal.isListening = true;
    }
  } else if(window[functionName] !== undefined) {
    retVal.isListening = true;
  } else if(isInIframe()) {
    retVal.isIFrame = true;
  }

  return retVal;
}

function dispatchPayload(payload, payloadType, functionName, targetOrigin, showAlert) {
  if(isIos()) {
    const callback = window.webkit.messageHandlers[functionName];
    if(callback !== undefined) {
      callback.postMessage(payload)
    }
  } else if(window[functionName] !== undefined) {
    window[functionName].postMessage(JSON.stringify(payload))
  } else if(isInIframe()) {
    window.parent.postMessage({
      type: payloadType.description,
      payload: payload
    }, targetOrigin)
  } else if(showAlert) {
    console.log(`${functionName}=${JSON.stringify(payload)}`);
    alert(`${functionName}=${JSON.stringify(payload)}`);
  }
  console.log(`${functionName}=${JSON.stringify(payload)}`);
}

function parseMessages(rawMsgJson) {
  const msgJson = rawMsgJson.replace(/&quot;/g, '"');
  return JSON.parse(msgJson);
}

function makeBackendJsonPostCall(url, payload) {

}

function setTheme(isDark) {
  isDark ? document.documentElement.setAttribute('theme', "dark")
      : document.documentElement.setAttribute('theme', "light");
}

function toggleMultiModal(elementId) {
  $(".multi-modal").hide()
  $(elementId).show()
}

function toggleStartForm(elementId) {
  $(".start-form").hide()
  $(elementId).show()
}

function disableButton(jqueryHtmlElementButton) {
  jqueryHtmlElementButton.attr('disabled', 'disabled');
}

// requires a button html element to have the follow attribute disabled="disabled"
function enableButton(jqueryHtmlElementButton) {
  jqueryHtmlElementButton.removeAttr('disabled');
}

function disableHref(jqueryHtmlElementButton) {
  jqueryHtmlElementButton.addClass("disabled")
}

function enableHref(jqueryHtmlElementButton) {
  jqueryHtmlElementButton.removeClass("disabled")
}

function isLikelyValidEmail (emailString) {
  const emailRegex = /(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])/
  return emailRegex.test(emailString);
}

function isLikelyValidSms (smsString) {
  const smsRegex = /[+][0-9]{6,15}/
  return smsRegex.test(smsString)
}

function captchaSubmit(formElementId, token) {
  console.log(`captchaToken=${token}`);
  document.getElementById(formElementId).requestSubmit();
}

function captchaSubmitEmail(token) {
  return captchaSubmit("email-start-form", token);
}

function captchaSubmitSms(token) {
  return captchaSubmit("sms-start-form", token);
}

function aFunctionThatShouldBeMissingIfOurCachingIsBroken() {
  console.log("Did we explode?");
}

//Using this approach https://stackoverflow.com/a/45436329
function coerceKeyboardEventToString(event) {
  const currentCode = event.which || event.code;
  let currentKey = event.key;
  if (!currentKey) {
    currentKey = String.fromCharCode(currentCode);
  }

  return currentKey;
}