import axios from "axios";

const backendUrl = process.env.REACT_APP_BACKEND_URL?.replace(/\/$/, "") ?? "";

export function redirectToLogin() {
  if (window.location.pathname.startsWith("/login")) {
    return;
  }
  const query = window.location.search;
  window.location.replace(`/login${query}`);
}

function shouldRedirectToLogin(error) {
  const status = error.response?.status;
  return status === 401 || status === 403;
}

const api = axios.create({
  baseURL: backendUrl ? `${backendUrl}/api` : "/api",
  withCredentials: true,
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (shouldRedirectToLogin(error)) {
      redirectToLogin();
    }
    return Promise.reject(error);
  }
);

export default api;
