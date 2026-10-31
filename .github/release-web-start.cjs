// Copied beside server.js in the downloadable web package.
// Local testing must not expose the unauthenticated development API to the LAN.
process.env.HOSTNAME = "127.0.0.1";
process.env.PORT ||= "3000";
require("./server.js");
