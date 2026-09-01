// JCEF 在 onLoadEnd 才注入 window.cbdHost；若在此捕获一次，会一直走到空 postMessage，按钮全无反应。
const vscodeApi = {
  postMessage: function (msg) {
    var payload = typeof msg === 'string' ? msg : JSON.stringify(msg);
    try {
      if (window.cbdHost && typeof window.cbdHost.postMessage === 'function') {
        window.cbdHost.postMessage(msg);
        return;
      }
    } catch (e) {}
    console.log('__CBD_MSG__' + payload);
  },
  getState: function () {
    if (window.cbdHost && typeof window.cbdHost.getState === 'function') return window.cbdHost.getState();
    return window.__cbdState || {};
  },
  setState: function (s) {
    if (window.cbdHost && typeof window.cbdHost.setState === 'function') window.cbdHost.setState(s);
    else window.__cbdState = s;
  }
};
function postDialog(msg) {
  var key = msg.type + ':' + (msg.docRel || msg.sourceRel || '');
  if (postDialog._busy && postDialog._busy[key]) return;
  postDialog._busy = postDialog._busy || {};
  postDialog._busy[key] = 1;
  setTimeout(function () { delete postDialog._busy[key]; }, 2500);
  vscodeApi.postMessage(msg);
}
const btnIr = document.getElementById('btnIr');
const btnSource = document.getElementById('btnSource');
const btnDelete = document.getElementById('btnDelete');
const btnRevealSource = document.getElementById('btnRevealSource');
const btnDirectoryDoc = document.getElementById('btnDirectoryDoc');
const btnBack = document.getElementById('btnBack');
const btnForward = document.getElementById('btnForward');
const btnHome = document.getElementById('btnHome');
const editorRoot = document.getElementById('editorRoot');
const editorBar = document.getElementById('editorBar');
const homeEl = document.getElementById('home');
const coveragePageEl = document.getElementById('coveragePage');
const unboundEl = document.getElementById('unbound');
const unboundPath = document.getElementById('unboundPath');
const unboundDirHint = document.getElementById('unboundDirHint');
const unboundDirPath = document.getElementById('unboundDirPath');
const unboundDirDocName = document.getElementById('unboundDirDocName');
const btnOpenDirDoc = document.getElementById('btnOpenDirDoc');
const btnCreate = document.getElementById('btnCreate');
const docList = document.getElementById('docList');
const missingSection = document.getElementById('missingSection');
const missingList = document.getElementById('missingList');
const hintSection = document.getElementById('hintSection');
const hintList = document.getElementById('hintList');
const coverageSection = document.getElementById('coverageSection');
const coverageStat = document.getElementById('coverageStat');
const coverageActions = document.getElementById('coverageActions');
const btnOpenCoverage = document.getElementById('btnOpenCoverage');
const coveragePageStat = document.getElementById('coveragePageStat');
const coveragePageList = document.getElementById('coveragePageList');
const hashBulkRow = document.getElementById('hashBulkRow');
const btnRefreshAllHashes = document.getElementById('btnRefreshAllHashes');
const homeDocsPath = document.getElementById('homeDocsPath');
const hint = document.getElementById('hint');
const mdSource = document.getElementById('mdSource');
let outlineEnable = window.cbdOutlineEnable !== false;
const assetWaiters = {};
let assetReqSeq = 0;
let vditor = null;
let irReady = false;
let pendingIrReveal = false;
let warmTimer = null;
let mode = 'ir';
let applying = false;
let pendingMarkdown = '';
let unboundSourceRel = '';
let unboundDirDocRel = '';
let currentDocRel = '';
let sourceJump = null;
let directoryDocRel = '';

function requestSaveAsset(file) {
  return new Promise(function (resolve, reject) {
    const requestId = 'a' + String(++assetReqSeq);
    const reader = new FileReader();
    reader.onerror = function () { reject(new Error('读取文件失败')); };
    reader.onload = function () {
      const dataUrl = String(reader.result || '');
      const comma = dataUrl.indexOf(',');
      const base64 = comma >= 0 ? dataUrl.slice(comma + 1) : dataUrl;
      assetWaiters[requestId] = { resolve: resolve, reject: reject };
      vscodeApi.postMessage({ type: 'saveAsset', requestId: requestId, fileName: file.name || 'paste.png', base64: base64, mime: file.type || '' });
      setTimeout(function () {
        if (assetWaiters[requestId]) { delete assetWaiters[requestId]; reject(new Error('保存资源超时')); }
      }, 20000);
    };
    reader.readAsDataURL(file);
  });
}
function onAssetSaved(msg) {
  const waiter = assetWaiters[msg.requestId];
  if (!waiter) return;
  delete assetWaiters[msg.requestId];
  if (msg.ok && msg.mdPath) waiter.resolve({ mdPath: msg.mdPath, previewSrc: msg.previewSrc || '' });
  else waiter.reject(new Error(msg.error || '保存失败'));
}
btnRefreshAllHashes.addEventListener('click', function () { postDialog({ type: 'refreshAllHashes' }); });
function isDark() { return true; }
function setModeButtons() {
  btnIr.classList.toggle('active', mode === 'ir');
  btnSource.classList.toggle('active', mode === 'source');
}
function setDeleteVisible(visible) { btnDelete.classList.toggle('hidden-mode', !visible); }
function setRevealSourceVisible(jump) {
  sourceJump = jump || null;
  btnRevealSource.classList.toggle('hidden-mode', !(jump && jump.path));
}
function setDirectoryDocVisible(directoryDoc) {
  directoryDocRel = directoryDoc && directoryDoc.doc ? directoryDoc.doc : '';
  btnDirectoryDoc.classList.toggle('hidden-mode', !directoryDocRel);
}
function setNav(canBack, canForward) {
  btnBack.disabled = !canBack;
  btnForward.disabled = !canForward;
}
function parkEditor() {
  editorBar.classList.add('hidden');
  if (vditor || editorRoot.classList.contains('warming')) {
    editorRoot.classList.remove('hidden');
    editorRoot.classList.add('warming');
  } else {
    editorRoot.classList.remove('warming');
    editorRoot.classList.add('hidden');
  }
}
function hideAll() {
  unboundEl.classList.remove('visible');
  homeEl.classList.remove('visible');
  coveragePageEl.classList.remove('visible');
  parkEditor();
}
function scheduleWarmIr(immediate) {
  if (vditor) return;
  if (warmTimer) { clearTimeout(warmTimer); warmTimer = null; }
  const run = function () {
    warmTimer = null;
    if (vditor) return;
    editorRoot.classList.remove('hidden');
    editorRoot.classList.add('warming');
    ensureIr('');
  };
  if (immediate) run(); else warmTimer = setTimeout(run, 80);
}
function showUnbound(sourceRel, canBack, canForward, canCreate, dirDoc) {
  hideAll();
  unboundSourceRel = sourceRel || '';
  currentDocRel = '';
  setDeleteVisible(false);
  setRevealSourceVisible(null);
  setDirectoryDocVisible(null);
  unboundPath.textContent = unboundSourceRel;
  unboundEl.classList.add('visible');
  btnCreate.style.display = canCreate !== false ? '' : 'none';
  hint.textContent = canCreate !== false ? '无关联文档 · 可新建绑定或返回主页' : '无关联文档';
  if (dirDoc && dirDoc.doc) {
    unboundDirDocRel = dirDoc.doc;
    unboundDirPath.textContent = (dirDoc.dirPath || '') + '/';
    unboundDirDocName.textContent = dirDoc.doc;
    unboundDirHint.classList.remove('hidden-mode');
    btnOpenDirDoc.style.display = '';
  } else {
    unboundDirDocRel = '';
    unboundDirHint.classList.add('hidden-mode');
    btnOpenDirDoc.style.display = 'none';
  }
  setNav(canBack, canForward);
  scheduleWarmIr(false);
}
function driftBadge(kind) {
  if (kind === 'missing-doc') return '文档缺失';
  if (kind === 'missing-target') return '源文件缺失';
  if (kind === 'hash') return '源码已变';
  if (kind === 'overlap') return '范围重叠';
  if (kind === 'range') return '行范围失效';
  if (kind === 'symbol') return '符号变动';
  return kind || '提醒';
}
function appendBtn(parent, label, className, onClick) {
  const btn = document.createElement('button');
  btn.type = 'button';
  if (className) btn.className = className;
  btn.textContent = label;
  btn.addEventListener('click', onClick);
  parent.appendChild(btn);
}
function showHome(docsPath, docs, missing, hints, canBack, canForward, coverage) {
  hideAll();
  currentDocRel = '';
  setDeleteVisible(false);
  setRevealSourceVisible(null);
  setDirectoryDocVisible(null);
  homeDocsPath.textContent = (docsPath || 'docs') + '/';
  docList.innerHTML = '';
  missingList.innerHTML = '';
  hintList.innerHTML = '';
  if (coverage && coverage.total > 0) {
    coverageSection.classList.remove('hidden-mode');
    const pct = Math.round((coverage.boundCount / coverage.total) * 100);
    coverageStat.textContent = '已绑定 ' + coverage.boundCount + ' / ' + coverage.total + ' 个源文件（' + pct + '%）';
    if (coverage.unboundCount > 0) {
      coverageActions.classList.remove('hidden-mode');
      btnOpenCoverage.textContent = '查看未绑定（' + coverage.unboundCount + '）';
    } else {
      coverageActions.classList.add('hidden-mode');
      coverageStat.textContent += ' · 全部可绑定源文件均已覆盖';
    }
  } else {
    coverageSection.classList.add('hidden-mode');
    coverageActions.classList.add('hidden-mode');
  }
  const missingItems = missing || [];
  if (missingItems.length) {
    missingSection.classList.remove('hidden-mode');
    missingItems.forEach(function (item) {
      const li = document.createElement('li');
      const card = document.createElement('div');
      card.className = 'missing-card';
      const badge = document.createElement('span');
      badge.className = 'badge';
      badge.textContent = driftBadge(item.kind);
      const title = document.createElement('strong');
      title.textContent = item.kind === 'missing-doc' ? item.target : (item.doc || item.target);
      const meta = document.createElement('span');
      meta.className = 'meta';
      meta.textContent = item.message;
      const actions = document.createElement('div');
      actions.className = 'actions';
      if (item.kind === 'missing-doc') {
        appendBtn(actions, '重新绑定', null, function () { postDialog({ type: 'createBind', sourceRel: item.target }); });
        appendBtn(actions, '打开源文件', 'secondary', function () { vscodeApi.postMessage({ type: 'openTarget', sourceRel: item.target }); });
      } else if (item.kind === 'missing-target') {
        appendBtn(actions, '重新绑定', null, function () { postDialog({ type: 'rebindDoc', docRel: item.doc }); });
        appendBtn(actions, '打开此文档', 'secondary', function () { vscodeApi.postMessage({ type: 'openDoc', docRel: item.doc }); });
        appendBtn(actions, '删除失效文档', 'danger', function () { postDialog({ type: 'deleteDoc', docRel: item.doc }); });
      } else if (item.kind === 'symbol' || item.kind === 'range') {
        if (String(item.message || '').indexOf('未找到符号') < 0) {
          appendBtn(actions, '按 symbol 重算行号', null, function () { postDialog({ type: 'retightenRange', docRel: item.doc }); });
        }
        appendBtn(actions, '重新绑定', 'secondary', function () { postDialog({ type: 'rebindDoc', docRel: item.doc }); });
        appendBtn(actions, '打开文档', 'secondary', function () { vscodeApi.postMessage({ type: 'openDoc', docRel: item.doc }); });
      } else {
        appendBtn(actions, '打开文档', null, function () { vscodeApi.postMessage({ type: 'openDoc', docRel: item.doc }); });
        appendBtn(actions, '重新绑定', 'secondary', function () { postDialog({ type: 'rebindDoc', docRel: item.doc }); });
      }
      card.appendChild(badge); card.appendChild(document.createElement('br'));
      card.appendChild(title); card.appendChild(meta); card.appendChild(actions);
      li.appendChild(card); missingList.appendChild(li);
    });
  } else missingSection.classList.add('hidden-mode');
  const hintItems = hints || [];
  if (hintItems.length) {
    hintSection.classList.remove('hidden-mode');
    hashBulkRow.classList.toggle('hidden-mode', hintItems.length < 2);
    hintItems.forEach(function (item) {
      const li = document.createElement('li');
      const card = document.createElement('div');
      card.className = 'hint-card';
      const badge = document.createElement('span'); badge.className = 'badge'; badge.textContent = driftBadge('hash');
      const title = document.createElement('strong'); title.textContent = item.doc || item.target;
      const meta = document.createElement('span'); meta.className = 'meta'; meta.textContent = item.message || ('源文件 ' + item.target + ' 已修改');
      const actions = document.createElement('div'); actions.className = 'actions';
      appendBtn(actions, '打开文档核对', null, function () { vscodeApi.postMessage({ type: 'openDoc', docRel: item.doc }); });
      appendBtn(actions, '标记已核对', 'secondary', function () { postDialog({ type: 'refreshHash', docRel: item.doc }); });
      card.appendChild(badge); card.appendChild(document.createElement('br'));
      card.appendChild(title); card.appendChild(meta); card.appendChild(actions);
      li.appendChild(card); hintList.appendChild(li);
    });
  } else { hintSection.classList.add('hidden-mode'); hashBulkRow.classList.add('hidden-mode'); }
  if (!docs || !docs.length) {
    const li = document.createElement('li'); li.textContent = '暂无绑定文档。打开源文件后可新建关联。'; docList.appendChild(li);
  } else renderBoundTree(docList, docs);
  homeEl.classList.add('visible');
  hint.textContent = missingItems.length ? ('主页 · ' + missingItems.length + ' 项绑定提醒') : (hintItems.length ? ('主页 · ' + hintItems.length + ' 项文档核对提醒') : '主页 · 选择文档打开');
  setNav(canBack, canForward);
  scheduleWarmIr(false);
}
function showCoveragePage(boundCount, total, unbound, canBack, canForward) {
  hideAll(); currentDocRel = ''; setDeleteVisible(false); setRevealSourceVisible(null); setDirectoryDocVisible(null);
  coveragePageList.innerHTML = '';
  const list = unbound || [];
  const unboundTotal = Math.max(0, (total || 0) - (boundCount || 0));
  const pct = total ? Math.round((boundCount / total) * 100) : 0;
  coveragePageStat.textContent = '已绑定 ' + boundCount + ' / ' + total + '（' + pct + '%） · 未绑定 ' + unboundTotal + ' 个';
  if (!list.length) {
    const li = document.createElement('li'); li.textContent = unboundTotal === 0 ? '全部可绑定源文件均已覆盖。' : '暂无未绑定项。'; coveragePageList.appendChild(li);
  } else {
    list.forEach(function (path) {
      const li = document.createElement('li'); const card = document.createElement('div'); card.className = 'hint-card';
      const title = document.createElement('code'); title.textContent = path;
      const actions = document.createElement('div'); actions.className = 'actions';
      appendBtn(actions, '新建绑定', null, function () { postDialog({ type: 'createBind', sourceRel: path }); });
      appendBtn(actions, '打开源文件', 'secondary', function () { vscodeApi.postMessage({ type: 'openTarget', sourceRel: path }); });
      card.appendChild(title); card.appendChild(actions); li.appendChild(card); coveragePageList.appendChild(li);
    });
  }
  coveragePageEl.classList.add('visible'); hint.textContent = '未绑定源文件 · 可新建绑定或打开 Code';
  setNav(canBack, canForward); scheduleWarmIr(false);
}
function escapeHtml(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
function bindingKindLabel(item) {
  if (item.kind === 'range' && item.startLine != null && item.endLine != null) {
    return item.symbol ? ('range L' + item.startLine + '-' + item.endLine + ' · ' + item.symbol) : ('range L' + item.startLine + '-' + item.endLine);
  }
  if (item.kind === 'index') return '汇总';
  if (item.kind === 'directory') return 'directory';
  return 'file';
}
function makeDocLink(item) {
  const a = document.createElement('a'); a.href = '#'; a.dataset.doc = item.doc;
  a.innerHTML = '<strong>' + escapeHtml(item.title || item.doc) + '</strong><span class="meta">' + escapeHtml(item.doc) + ' · ' + escapeHtml(bindingKindLabel(item)) + '</span>';
  a.addEventListener('click', function (e) { e.preventDefault(); vscodeApi.postMessage({ type: 'openDoc', docRel: item.doc }); });
  return a;
}
function loadBoundTreeCollapsed() {
  try { const st = vscodeApi.getState() || {}; return new Set(Array.isArray(st.boundTreeCollapsed) ? st.boundTreeCollapsed : []); } catch (e) { return new Set(); }
}
function saveBoundTreeCollapsed(collapsed) {
  try { const prev = vscodeApi.getState() || {}; vscodeApi.setState(Object.assign({}, prev, { boundTreeCollapsed: Array.from(collapsed) })); } catch (e) {}
}
function renderBoundTree(container, docs) {
  const indexItems = []; const root = { name: '', children: new Map(), bindings: [] }; const collapsedFolders = loadBoundTreeCollapsed();
  docs.forEach(function (item) {
    if (item.kind === 'index' || item.target === '(汇总)') { indexItems.push(item); return; }
    const parts = String(item.target || '').split('/').filter(Boolean);
    if (!parts.length) { root.bindings.push(item); return; }
    const isDirectory = item.kind === 'directory';
    let node = root;
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]; const isLeaf = i === parts.length - 1;
      if (!node.children.has(part)) node.children.set(part, { name: part, children: new Map(), bindings: [], isFile: false });
      const child = node.children.get(part);
      if (!isLeaf || isDirectory) child.isFile = false;
      if (isLeaf) { if (!isDirectory && child.children.size === 0) child.isFile = true; child.bindings.push(item); }
      node = child;
    }
  });
  indexItems.forEach(function (item) { const li = document.createElement('li'); li.className = 'index-item leaf'; li.appendChild(makeDocLink(item)); container.appendChild(li); });
  function renderNode(node, parentEl, pathPrefix) {
    Array.from(node.children.values()).sort(function (a, b) { if (a.isFile !== b.isFile) return a.isFile ? 1 : -1; return a.name.localeCompare(b.name); }).forEach(function (child) {
      const li = document.createElement('li'); const childPath = pathPrefix ? pathPrefix + '/' + child.name : child.name;
      if (!child.isFile) {
        const isCollapsed = collapsedFolders.has(childPath);
        const row = document.createElement('div'); row.className = 'folder-row';
        const twist = document.createElement('span'); twist.className = 'twist'; twist.textContent = isCollapsed ? '▸' : '▾';
        const name = document.createElement('span'); name.textContent = child.name + '/';
        row.appendChild(twist); row.appendChild(name); li.appendChild(row);
        const ul = document.createElement('ul'); if (isCollapsed) ul.classList.add('collapsed');
        child.bindings.slice().sort(function (a, b) { return String(a.doc).localeCompare(String(b.doc)); }).forEach(function (binding) {
          const leaf = document.createElement('li'); leaf.className = 'leaf'; leaf.appendChild(makeDocLink(binding)); ul.appendChild(leaf);
        });
        renderNode(child, ul, childPath); li.appendChild(ul);
        row.addEventListener('click', function () {
          const nowCollapsed = ul.classList.toggle('collapsed');
          twist.textContent = nowCollapsed ? '▸' : '▾';
          if (nowCollapsed) collapsedFolders.add(childPath); else collapsedFolders.delete(childPath);
          saveBoundTreeCollapsed(collapsedFolders);
        });
      } else {
        li.className = 'file-row';
        const sourcePath = (child.bindings[0] && child.bindings[0].target) || childPath;
        const label = document.createElement('span'); label.className = 'file-label'; label.textContent = child.name;
        label.addEventListener('click', function (e) { e.preventDefault(); vscodeApi.postMessage({ type: 'openTarget', sourceRel: sourcePath }); });
        li.appendChild(label);
        const ul = document.createElement('ul');
        child.bindings.slice().sort(function (a, b) { return String(a.doc).localeCompare(String(b.doc)); }).forEach(function (binding) {
          const leaf = document.createElement('li'); leaf.className = 'leaf'; leaf.appendChild(makeDocLink(binding)); ul.appendChild(leaf);
        });
        li.appendChild(ul);
      }
      parentEl.appendChild(li);
    });
    node.bindings.forEach(function (binding) { const leaf = document.createElement('li'); leaf.className = 'leaf'; leaf.appendChild(makeDocLink(binding)); parentEl.appendChild(leaf); });
  }
  renderNode(root, container, '');
}
function showModePane(want) {
  document.getElementById('vditorIr').classList.toggle('hidden-mode', want !== 'ir');
  mdSource.classList.toggle('hidden-mode', want !== 'source');
}
function updateHint() { hint.textContent = mode === 'source' ? '文档源码 · YAML 头已隐藏 · 可直接编辑' : '输入 Markdown 即时渲染 · YAML 头已隐藏'; }
function showEditor(canBack, canForward) {
  unboundEl.classList.remove('visible'); homeEl.classList.remove('visible');
  editorRoot.classList.remove('hidden'); editorRoot.classList.remove('warming'); editorBar.classList.remove('hidden');
  updateHint(); setNav(canBack, canForward);
  if (mode === 'ir' && vditor && irReady) requestAnimationFrame(fitEditor);
}
function currentValue() {
  if (mode === 'source' || pendingIrReveal) return mdSource.value;
  if (vditor) { try { return vditor.getValue(); } catch (e) { return pendingMarkdown; } }
  return pendingMarkdown;
}
function fitEditor() {
  try {
    const el = document.querySelector('#vditorIr .vditor');
    if (!el) return;
    el.style.height = '100%';
    if (vditor && vditor.vditor && vditor.vditor.options) vditor.vditor.options.height = '100%';
  } catch (e) {}
}
function irScroller() {
  return document.querySelector('.vditor-ir pre.vditor-reset');
}
document.addEventListener('wheel', function (e) {
  if (mode !== 'ir') return;
  const reset = irScroller();
  if (!reset || !reset.contains(e.target) || e.target === reset) return;
  const dy = e.deltaY;
  if (!dy) return;
  let node = e.target;
  while (node && node !== reset) {
    if (node.scrollHeight > node.clientHeight + 1) {
      const atTop = node.scrollTop <= 0;
      const atBottom = node.scrollTop + node.clientHeight >= node.scrollHeight - 1;
      if ((dy < 0 && !atTop) || (dy > 0 && !atBottom)) return;
    }
    node = node.parentElement;
  }
  reset.scrollTop += dy;
  e.preventDefault();
}, { passive: false, capture: true });
function ensureIr(initialValue) {
  if (vditor || typeof Vditor === 'undefined') return false;
  applying = true; irReady = false;
  document.getElementById('vditorIr').innerHTML = '';
  vditor = new Vditor('vditorIr', {
    height: '100%', mode: 'ir', value: initialValue || '',
    cdn: './vditor', cache: { enable: false }, lang: 'zh_CN',
    outline: { enable: outlineEnable, position: 'right' },
    toolbarConfig: { pin: true, hide: false },
    toolbar: ['headings', 'bold', 'italic', 'strike', '|', 'list', 'ordered-list', 'check', '|', 'quote', 'code', 'inline-code', 'link', 'table', 'upload', '|', 'undo', 'redo', 'outline'],
    theme: isDark() ? 'dark' : 'classic',
    preview: { theme: { current: isDark() ? 'dark' : 'light' }, hljs: { enable: false } },
    upload: {
      accept: 'image/*',
      handler: function (files) {
        const list = Array.prototype.slice.call(files || []);
        if (!list.length) return null;
        return Promise.all(list.map(function (f) { return requestSaveAsset(f); })).then(function (results) {
          return results.map(function (r) { return '![](' + (r.previewSrc || r.mdPath) + ')'; }).join('\n');
        }).catch(function () { return ''; });
      }
    },
    after: function () {
      applying = false; irReady = true;
      fitEditor();
      if (pendingIrReveal && mode === 'ir') { pendingIrReveal = false; showModePane('ir'); }
    },
    input: function (value) {
      if (applying) return;
      pendingMarkdown = value;
      vscodeApi.postMessage({ type: 'markdownChanged', markdown: value });
    }
  });
  return true;
}
function setIrValue(markdown) {
  if (!vditor) return;
  applying = true;
  try { vditor.setValue(markdown || '', true); } catch (e) {}
  setTimeout(function () { applying = false; }, 0);
}
function setSourceValue(markdown) { applying = true; mdSource.value = markdown || ''; pendingMarkdown = mdSource.value; applying = false; }
function revealIrWhenQuiet() {
  if (mode !== 'ir') return;
  pendingIrReveal = false; showModePane('ir');
  fitEditor();
}
function applyMarkdown(markdown, nextMode, canBack, canForward, docRel, deletable, jump, directoryDoc) {
  pendingMarkdown = markdown || ''; currentDocRel = docRel || '';
  setDeleteVisible(!!deletable); setRevealSourceVisible(jump); setDirectoryDocVisible(directoryDoc);
  mode = nextMode === 'source' ? 'source' : 'ir'; setModeButtons(); showEditor(canBack, canForward);
  if (mode === 'source') { pendingIrReveal = false; setSourceValue(pendingMarkdown); showModePane('source'); return; }
  setSourceValue(pendingMarkdown); showModePane('source');
  if (!vditor) { pendingIrReveal = true; ensureIr(pendingMarkdown); return; }
  pendingIrReveal = true; setIrValue(pendingMarkdown);
  requestAnimationFrame(function () { requestAnimationFrame(revealIrWhenQuiet); });
}
function switchModeLocal(want) {
  const next = want === 'source' ? 'source' : 'ir';
  if (next === mode) return;
  pendingMarkdown = currentValue(); mode = next; setModeButtons(); updateHint();
  if (next === 'source') { pendingIrReveal = false; setSourceValue(pendingMarkdown); showModePane('source'); }
  else {
    showModePane('source');
    if (!vditor) { pendingIrReveal = true; ensureIr(pendingMarkdown); }
    else { pendingIrReveal = true; setIrValue(pendingMarkdown); requestAnimationFrame(function () { requestAnimationFrame(revealIrWhenQuiet); }); }
  }
  vscodeApi.postMessage({ type: 'switchMode', mode: next, markdown: pendingMarkdown });
}
mdSource.addEventListener('input', function () {
  if (applying) return;
  pendingMarkdown = mdSource.value;
  vscodeApi.postMessage({ type: 'markdownChanged', markdown: pendingMarkdown });
});
btnIr.addEventListener('click', function () { switchModeLocal('ir'); });
btnSource.addEventListener('click', function () { switchModeLocal('source'); });
btnRevealSource.addEventListener('click', function () {
  if (!sourceJump || !sourceJump.path) return;
  vscodeApi.postMessage({ type: 'openTarget', sourceRel: sourceJump.path, startLine: sourceJump.startLine, endLine: sourceJump.endLine, kind: sourceJump.kind });
});
btnDirectoryDoc.addEventListener('click', function () { if (directoryDocRel) vscodeApi.postMessage({ type: 'openDoc', docRel: directoryDocRel }); });
btnDelete.addEventListener('click', function () {
  if (!currentDocRel || btnDelete.dataset.busy) return;
  btnDelete.dataset.busy = '1';
  setTimeout(function () { delete btnDelete.dataset.busy; }, 2500);
  postDialog({ type: 'deleteDoc', docRel: currentDocRel });
});
btnCreate.addEventListener('click', function () {
  if (!unboundSourceRel || btnCreate.dataset.busy) return;
  btnCreate.dataset.busy = '1';
  setTimeout(function () { delete btnCreate.dataset.busy; }, 2500);
  scheduleWarmIr(true);
  postDialog({ type: 'createBind', sourceRel: unboundSourceRel });
});
btnOpenDirDoc.addEventListener('click', function () { if (unboundDirDocRel) vscodeApi.postMessage({ type: 'openDoc', docRel: unboundDirDocRel }); });
btnHome.addEventListener('click', function () { vscodeApi.postMessage({ type: 'navHome' }); });
btnOpenCoverage.addEventListener('click', function () { vscodeApi.postMessage({ type: 'navCoverage' }); });
btnBack.addEventListener('click', function () { vscodeApi.postMessage({ type: 'navBack' }); });
btnForward.addEventListener('click', function () { vscodeApi.postMessage({ type: 'navForward' }); });
window.addEventListener('message', function (event) {
  const msg = event.data;
  if (!msg) return;
  if (msg.type === 'warmIr') { scheduleWarmIr(true); return; }
  if (msg.type === 'unbound') { showUnbound(msg.sourceRel || '', msg.canBack, msg.canForward, msg.canCreate !== false, msg.dirDoc || null); return; }
  if (msg.type === 'assetSaved') { onAssetSaved(msg); return; }
  if (msg.type === 'home') { showHome(msg.docsPath, msg.docs || [], msg.missing || [], msg.hints || [], msg.canBack, msg.canForward, msg.coverage || null); return; }
  if (msg.type === 'coverage') { showCoveragePage(msg.boundCount || 0, msg.total || 0, msg.unbound || [], msg.canBack, msg.canForward); return; }
  if (msg.type !== 'load') return;
  applyMarkdown(msg.markdown || '', msg.mode, msg.canBack, msg.canForward, msg.docRel || '', !!msg.deletable, msg.sourceJump || null, msg.directoryDoc || null);
});
vscodeApi.postMessage({ type: 'ready' });
window.addEventListener('resize', function () {
  if (mode === 'ir' && vditor && irReady) fitEditor();
});
if (window.ResizeObserver) {
  new ResizeObserver(function () {
    if (mode === 'ir' && vditor && irReady && !editorRoot.classList.contains('warming')) fitEditor();
  }).observe(editorRoot);
}
