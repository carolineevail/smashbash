var templates = {};
  templates.events = [
    "<div class = 'my-event' id = '<%= userName %>'>", //REMOVE THE UNDERSCORE ON ID WHEN USING ROUTES
    "<h6> <%= eventName %> </h6>",
    "<h6> <%= eventLocation %> </h6>",
    "<h6> <%= time %> </h6>",
    "<h6> <%= date %> </h6>",
    "<h6> <%= description %> </h6>",
    "<img src = '<%= image %>'>",
  ].join("");
