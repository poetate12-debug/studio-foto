import { readFileSync } from "node:fs";

const html = readFileSync("index.html", "utf8");
const sw = readFileSync("sw.js", "utf8");
const fail = (message) => { console.error("[smoke-check] FAIL:", message); process.exitCode = 1; };
const pass = (message) => console.log("[smoke-check] OK:", message);

function requireSource(condition, message) {
  if (condition) pass(message);
  else fail(message);
}

const renderStart = html.indexOf("function render(){");
const renderEnd = html.indexOf("\nfunction showModal", renderStart);
const renderSource = renderStart >= 0 && renderEnd > renderStart ? html.slice(renderStart, renderEnd) : "";
const navStart = html.indexOf("function navTo(");
const navEnd = html.indexOf("\nfunction head(", navStart);
const navSource = navStart >= 0 && navEnd > navStart ? html.slice(navStart, navEnd) : "";

requireSource(renderSource.length > 0, "render() route handler exists");
requireSource(!/\$\s*\([^)]*\)\s*\.\s*forEach\s*\(/.test(renderSource), "render() does not call forEach on a single-element selector");
requireSource(!/(?<!\$)\$\s*\([^)]*\)\s*\.\s*(forEach|map|filter)\s*\(/.test(html), "no collection methods are called on a single-element selector anywhere in the app");
requireSource(renderSource.includes("setActivePageNav(page)"), "render() uses the shared safe navigation helper");
requireSource(navSource.includes("setActivePageNav(page)"), "navTo() uses the shared safe navigation helper");
requireSource(html.includes("function renderErrorScreen("), "render errors have an in-app recovery screen");
requireSource(renderSource.includes("catch(error)") && renderSource.includes("renderErrorScreen(error,requestedPage)"), "render() catches synchronous page-render errors");
requireSource(html.includes("Promise.allSettled([dbGetAll('projects'),dbGetAll('templates')])"), "project/template storage loading fails independently");
requireSource(html.includes("function renderProjects()") && html.includes("function renderProjectGallery()") && html.includes("function renderPhotoEditor()"), "main list, project photo gallery and editor are separate pages");
requireSource(html.includes("key:'light'") && html.includes("key:'cutout'") && html.includes("key:'template'") && html.includes("key:'face'") && html.includes("key:'export'"), "five editing stages are present in order");
const orderedStageKeys = ["key:'light'", "key:'cutout'", "key:'template'", "key:'face'", "key:'export'"].map((key) => html.indexOf(key));
requireSource(orderedStageKeys.every((index, i) => index >= 0 && (i === 0 || index > orderedStageKeys[i - 1])), "stages appear in the required order");
requireSource(html.includes("function wireNav()") && html.includes("function navTo(next)"), "navigation is wired and has a route guard");
requireSource(html.includes("function setActivePageNav(nextPage)") && html.includes("Array.from(document.querySelectorAll('[data-page]')).forEach"), "navigation safely iterates a real array of page buttons");
requireSource(html.includes("function invalidatePhotoStages("), "editing an earlier stage invalidates dependent results");
requireSource(html.includes("function bindThumbOnlyRanges()") && html.includes('input.style.pointerEvents="none"') && html.includes("hitRadius=13"), "range sliders only start dragging when the thumb is touched");
requireSource(html.includes("const update=(key,value)=>") && html.includes("lightSaveTimer=setTimeout"), "light slider preview updates synchronously and storage writes are debounced");
requireSource(html.includes("if(stageIndex===0&&initialLightBrush.scope==='local')lightBrushEnabledPhotoId=ph.id;"), "restored local scope automatically activates the brush");
requireSource(html.includes("globalControls.classList.toggle('hidden',!!localMode)"), "local scope hides global sliders and global scope restores them");
requireSource(html.includes("localScopeSelectedInUi=!!document.getElementById('pf-light-scope-local')?.classList.contains('active')"), "finishing a photo is guarded by the visible local-selection mode");
requireSource(html.includes("paintLightBrushSegment(target,point,point") && html.includes("brush.masks[stroke.mode]=lightBrushCanvasHasPaint(canvas)?canvas.toDataURL('image/png'):null"), "brush strokes render and persist selection masks");
requireSource(html.includes('id="pf-reset-light"') && html.includes("Cahaya direset ke Original."), "light stage includes a reset-to-original control");
requireSource(html.includes("touch-action:pan-y") && html.includes("--pf-range-fill"), "range sliders support vertical scrolling and visible progress fill");
requireSource(html.includes("function stageCanBeOpened(p,targetIndex)") && html.includes("Selesaikan minimal satu foto pada tahap"), "next stage can open after at least one eligible photo");
requireSource(html.includes("pf-finish-button.next") && html.includes("pf-finish-button.complete"), "continue and completed status use different colors");
requireSource(html.includes("async function saveCanvasToDevice("), "export has a device-save path");
requireSource(html.includes("native-download.bundle.js"), "native download bridge is referenced");
requireSource(sw.includes("reang-studio-foto-v5") && sw.includes("const isAppShell"), "service worker cache is versioned and app shell is network-first");

const inlineScripts = [];
let cursor = 0;
while (true) {
  const open = html.indexOf("<script", cursor);
  if (open < 0) break;
  const start = html.indexOf(">", open) + 1;
  const end = html.indexOf("</script>", start);
  if (start === 0 || end < 0) { fail("script tags are balanced"); break; }
  if (!html.slice(open, start).includes("src=")) inlineScripts.push(html.slice(start, end));
  cursor = end + "</script>".length;
}
requireSource(inlineScripts.length >= 1, "inline application script exists");

if (process.exitCode) process.exit(process.exitCode);
console.log("[smoke-check] All static checks passed.");
