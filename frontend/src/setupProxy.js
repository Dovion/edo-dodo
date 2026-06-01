const { createProxyMiddleware } = require("http-proxy-middleware");

const target = process.env.BACKEND_URL || "http://localhost:8080";

module.exports = function (app) {
  const proxyOptions = {
    target,
    changeOrigin: true,
    xfwd: true,
    cookieDomainRewrite: "localhost",
  };

  app.use("/api", createProxyMiddleware(proxyOptions));
  app.use("/logout", createProxyMiddleware(proxyOptions));
  app.post("/login", createProxyMiddleware(proxyOptions));
};
