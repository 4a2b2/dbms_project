/* 公共前端工具：token 管理 + API 封装 + 提示 */

const TokenStore = {
    get() { return localStorage.getItem('token'); },
    set(t) { localStorage.setItem('token', t); },
    clear() { localStorage.removeItem('token'); localStorage.removeItem('username'); },
    username() { return localStorage.getItem('username'); }
};

/** 统一 API 调用：自动带 token，统一抛错 {message, status} */
async function api(path, options = {}) {
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    const token = TokenStore.get();
    if (token) headers['Authorization'] = 'Bearer ' + token;
    const resp = await fetch(path, { ...options, headers });
    if (resp.status === 401) {
        TokenStore.clear();
        if (!location.pathname.startsWith('/s/') && location.pathname !== '/login') {
            location.href = '/login';
        }
        throw new Error('请先登录');
    }
    let data = null;
    const text = await resp.text();
    if (text) {
        try { data = JSON.parse(text); } catch { data = null; }
    }
    if (!resp.ok) {
        throw new Error((data && data.error) ? data.error : ('请求失败 (' + resp.status + ')'));
    }
    return data;
}

/** 在页面顶部显示提示 */
function showNotice(message, type = 'error') {
    const el = document.getElementById('notice');
    if (!el) { alert(message); return; }
    el.textContent = message;
    el.className = 'notice ' + type;
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function escapeHtml(s) {
    if (s == null) return '';
    return String(s).replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
}

/** 需要登录的页面调用：无 token 直接跳登录页 */
function requireLogin() {
    if (!TokenStore.get()) location.href = '/login';
}

function renderUserArea() {
    const el = document.getElementById('userArea');
    if (!el) return;
    const name = TokenStore.username();
    if (name) {
        el.innerHTML = `<span>${escapeHtml(name)}</span><a href="javascript:void(0)" id="logoutBtn">退出</a>`;
        document.getElementById('logoutBtn').onclick = () => { TokenStore.clear(); location.href = '/login'; };
    }
}
