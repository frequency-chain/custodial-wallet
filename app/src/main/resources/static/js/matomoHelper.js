class MatomoHelper {
    _paq;
    event;
    constructor(enabled, matomoUrl, siteId, enableHeartBeat, title, dimensions, event) {
        this._paq = this.register(enabled, matomoUrl, siteId, enableHeartBeat, title, dimensions)
        this.event = event;
    }

    register(enabled, matomoUrl, siteId, enableHeartBeat, title, dimensions) {
        if(enabled && matomoUrl && siteId) {
            var _paq = window._paq = window._paq || [];
            if (enableHeartBeat) { _paq.push(['enableHeartBeatTimer']); }
            if(title) { _paq.push(['setDocumentTitle', title]); }
            if(dimensions) { this.registerDimensions(_paq, dimensions); }
            _paq.push(['trackPageView']);
            _paq.push(['enableLinkTracking']);

            (function() {
                var u= matomoUrl;
                _paq.push(['setTrackerUrl', u+'matomo.php']);
                _paq.push(['setSiteId', siteId]);
                var d=document, g=d.createElement('script'), s=d.getElementsByTagName('script')[0];
                g.async=true; g.src='https://cdn.matomo.cloud/dsnp.matomo.cloud/matomo.js'; s.parentNode.insertBefore(g,s);
            })();
            return _paq
        }
    }

    registerDimensions(_paq, customDimensions) {
        if(enabled) {
            for (let i = 0; i < customDimensions.length; ++i) {
                const dimension = customDimensions[i];
                _paq.push(['setCustomDimension', window.customDimensionId = dimension.index, window.customDimensionValue = dimension.value]);
            }
        }
    }

    trackEvent(action) {
        if(enabled && this.event) {
            _paq.push(['trackEvent', this.event.category, this.event.page, action]);
        }
    }

    trackPageEvent(page, action) {
        if(enabled && this.event) {
            _paq.push(['trackEvent', this.event.category, page, action]);
        }
    }

    trackFullEvent(category, page, action) {
        if(enabled) {
            _paq.push(['trackEvent', category, page, action]);
        }
    }

    trackGoal(goalId) {
        if (enabled) {
            _paq.push(['trackGoal', goalId]);
        }
    }
}
