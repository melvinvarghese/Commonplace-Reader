// This classic script must run before imports: a module parse failure cannot report itself.
(function () {
  function send(type, stage, message) {
    window.HybridBridge.message(JSON.stringify({ type: type, stage: stage, message: message }));
  }
  window.addEventListener('error', function (event) {
    var target = event.target;
    var resource = target && (target.src || target.href);
    var message = event.message || ('Local resource failed: ' + (resource || 'unknown module'));
    if (event.filename) message += ' @' + event.filename + ':' + event.lineno;
    send('error', 'global-error', message);
  }, true);
  window.addEventListener('unhandledrejection', function (event) {
    var reason = event.reason;
    send('error', 'unhandled-rejection', String(reason && reason.message || reason));
  });
  send('diagnostic', 'bootstrap-ready');
}());
