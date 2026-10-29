import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";

const html = readFileSync("index.html", "utf8");
const sw = readFileSync("sw.js", "utf8");
const androidWorkflow = readFileSync(".github/workflows/android-apk.yml", "utf8");
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
requireSource(html.includes("function runProjectCutout(p,ph)") && html.includes("fal-ai/imageutils/rembg") && html.includes("fal-ai/birefnet") && html.includes("fal-ai/bria/background/remove"), "project cutout offers economy, balanced, and high-quality AI model choices");
const cutoutHandlerStart = html.indexOf("if(stageIndex===1){\\n  $$('[data-cutout-mode]')", html.indexOf("function renderPhotoEditor"));
const cutoutHandlerEnd = html.indexOf("if(stageIndex===2)$('#pf-template')", cutoutHandlerStart);
const cutoutHandlerSource = cutoutHandlerStart >= 0 && cutoutHandlerEnd > cutoutHandlerStart ? html.slice(cutoutHandlerStart, cutoutHandlerEnd) : "";
requireSource(cutoutHandlerSource.includes("runProjectCutout(p,ph)") && !cutoutHandlerSource.includes("page='tool-runner'") && !cutoutHandlerSource.includes("workflowReturn={projectId:p.id,photoId:ph.id,stageKey:'cutout'}"), "cutout runs inside the project editor without navigating to the separate AI tool page");
requireSource(html.includes("function bindProjectCutoutBrush(p,ph)") && html.includes("Tambah subjek / pulihkan") && html.includes("Hapus / transparan") && html.includes("destination-out"), "cutout result supports manual add-subject and remove-transparent brush corrections");
requireSource(html.includes("ph.workflowResults.cutoutSource=source") && html.includes("ph.workflowResults.cutout=cutoutWorkingCanvas.toDataURL('image/png')"), "cutout source and manual corrections are stored separately in the project");
const orderedStageKeys = ["key:'light'", "key:'cutout'", "key:'template'", "key:'face'", "key:'export'"].map((key) => html.indexOf(key));
requireSource(orderedStageKeys.every((index, i) => index >= 0 && (i === 0 || index > orderedStageKeys[i - 1])), "stages appear in the required order");
requireSource(html.includes("function wireNav()") && html.includes("function navTo(next)"), "navigation is wired and has a route guard");
requireSource(html.includes("function setActivePageNav(nextPage)") && html.includes("Array.from(document.querySelectorAll('[data-page]')).forEach"), "navigation safely iterates a real array of page buttons");
requireSource(html.includes("function invalidatePhotoStages("), "editing an earlier stage invalidates dependent results");
requireSource(html.includes("function bindThumbOnlyRanges()") && html.includes('input.style.pointerEvents="none"') && html.includes("hitRadius=13"), "range sliders only start dragging when the thumb is touched");
requireSource(html.includes("const update=(key,value)=>") && html.includes("lightSaveTimer=setTimeout"), "light slider preview updates synchronously and storage writes are debounced");
requireSource(html.includes("if(stageIndex===0&&initialLightBrush.scope==='local'){lightBrushEnabledPhotoId=ph.id;initialLightBrush.mode='select';initialLightBrush.erase=false;if(!hasExplicitLightSelection(ph))initialLightBrush.selectionDone=false;}"), "restored local scope automatically activates the selection-only brush");
const brushStateStart = html.indexOf("function ensureLightBrushState(");
const brushStateEnd = html.indexOf("\nfunction lightBrushMaskKey", brushStateStart);
let brushStateReferenceTestPassed = false;
if (brushStateStart >= 0 && brushStateEnd > brushStateStart) {
  try {
    const ensureBrushState = runInNewContext(html.slice(brushStateStart, brushStateEnd) + "\nensureLightBrushState;");
    const photo = { adjust: { lightScope: "local" } };
    const capturedBrush = ensureBrushState(photo);
    ensureBrushState(photo); // redraw/preview reads state between pointerdown and pointerup
    capturedBrush.masks.select = "data:image/png;base64,selection-mask";
    capturedBrush.adjustments.exposure = 65;
    const currentBrush = ensureBrushState(photo);
    brushStateReferenceTestPassed =
      capturedBrush === currentBrush &&
      currentBrush.masks.select === "data:image/png;base64,selection-mask" &&
      currentBrush.adjustments.exposure === 65 &&
      currentBrush.scope === "local";
  } catch (error) {
    console.error("[smoke-check] Brush state reference regression test threw:", error);
  }
}
requireSource(brushStateReferenceTestPassed, "brush state identity, selection masks, and local adjustments survive repeated preview reads");
const mapStart = html.indexOf("function mapBrushPointerToCanvas(");
const mapEnd = html.indexOf("\nfunction renderLightBrushSelection", mapStart);
let brushCoordinateTestPassed = false;
if (mapStart >= 0 && mapEnd > mapStart) {
  try {
    const mapPoint = runInNewContext(html.slice(mapStart, mapEnd) + "\nmapBrushPointerToCanvas;");
    const mapped = mapPoint(155.5, 76.2, {left:40,top:30,width:231,height:115.5}, 100, 50);
    brushCoordinateTestPassed = Math.abs(mapped.x-50)<0.001 && Math.abs(mapped.y-20)<0.001 && mapped.displayWidth===100;
    const clamped = mapPoint(-100,-100,{left:40,top:30,width:231,height:115.5},100,50);
    brushCoordinateTestPassed = brushCoordinateTestPassed && clamped.x===0 && clamped.y===0;
  } catch (error) {
    console.error("[smoke-check] Brush coordinate unit test threw:", error);
  }
}
requireSource(brushCoordinateTestPassed, "brush pointer coordinates map correctly through zoom and clamp to the image bounds");
requireSource(html.includes("function blendMaskedLightPixels(baseData,adjustedData,maskData)") && html.includes("blendMaskedLightPixels(base.data,adjusted.data,maskData)"), "area-light preview blends adjusted pixels only by the selection mask");
const blendStart = html.indexOf("function blendMaskedLightPixels(");
const blendEnd = html.indexOf("\nasync function applyMaskedLocalLightAdjustmentsToCanvas", blendStart);
let blendUnitTestPassed = false;
if (blendStart >= 0 && blendEnd > blendStart) {
  try {
    const blend = runInNewContext(html.slice(blendStart, blendEnd) + "\nblendMaskedLightPixels;");
    const base = new Uint8ClampedArray([10,20,30,255, 10,20,30,255, 10,20,30,255]);
    const adjusted = new Uint8ClampedArray([110,120,130,255, 110,120,130,255, 110,120,130,255]);
    const mask = new Uint8ClampedArray([0,0,0,0, 0,0,0,128, 0,0,0,255]);
    blend(base, adjusted, mask);
    blendUnitTestPassed = base[0] === 10 && base[1] === 20 && base[2] === 30 &&
      base[4] === 60 && base[5] === 70 && base[6] === 80 &&
      base[8] === 110 && base[9] === 120 && base[10] === 130;
  } catch (error) {
    console.error("[smoke-check] Local mask blend unit test threw:", error);
  }
}
requireSource(blendUnitTestPassed, "masked-light blend preserves unselected pixels and changes partially/fully selected pixels");
requireSource(androidWorkflow.includes("secrets.ANDROID_DEBUG_KEYSTORE_BASE64") && androidWorkflow.includes("base64 --decode") && androidWorkflow.includes("Missing repository Actions secret"), "Android APK publishing requires the same persistent signing key for upgrades");
requireSource(androidWorkflow.includes("id.reang.studiofoto.v2") && androidWorkflow.includes("Verify APK can coexist with the old installation") && androidWorkflow.includes("dump badging"), "recovery APK uses a different package ID and is verified before publishing");
requireSource(androidWorkflow.includes('storeFile file(System.getProperty("user.home") + "/.android/debug.keystore")') && androidWorkflow.includes("debug { signingConfig signingConfigs.debug }") && androidWorkflow.includes("verify --print-certs") && androidWorkflow.includes("APK signature does not match the persistent repository signing key"), "generated Gradle debug signing is explicitly pinned and APK certificates are checked before publish");
requireSource(sw.includes("reang-studio-foto-v9") && sw.includes("const isAppShell"), "service worker cache is bumped and app shell remains network-first");
requireSource(html.includes("globalControls.classList.toggle('hidden',!!localMode)"), "local scope hides global sliders and global scope restores them");
requireSource(html.includes("localScopeSelectedInUi=!!document.getElementById('pf-light-scope-local')?.classList.contains('active')"), "finishing a photo is guarded by the visible local-selection mode");
requireSource(html.includes("paintLightBrushSegment(target,point,point") && html.includes("brush.masks[stroke.mode]=lightBrushCanvasHasPaint(canvas)?canvas.toDataURL('image/png'):null"), "brush strokes render and persist selection masks");
requireSource((html.slice(html.indexOf("function lightBrushPanelMarkup"),html.indexOf("function paintLightBrushSegment"))).includes('id="pf-brush-light-controls"') && !(html.slice(html.indexOf("function lightBrushPanelMarkup"),html.indexOf("function paintLightBrushSegment")).includes('data-pf-brush-mode')), "selected-area light sliders are shown without brighten/darken/eraser mode buttons");
requireSource(html.includes("brush.masks.select)brush.selectionDone=true;"), "adjusting local sliders completes and persists the selected-area workflow");
requireSource(html.includes('id="pf-reset-light"') && html.includes("Cahaya direset ke Original."), "light stage includes a reset-to-original control");
requireSource(html.includes("touch-action:pan-y") && html.includes("--pf-range-fill"), "range sliders support vertical scrolling and visible progress fill");
requireSource(html.includes("function stageCanBeOpened(p,targetIndex)") && html.includes("Selesaikan minimal satu foto pada tahap"), "next stage can open after at least one eligible photo");
requireSource(html.includes("pf-finish-button.next") && html.includes("pf-finish-button.complete"), "continue and completed status use different colors");
requireSource(html.includes("async function saveCanvasToDevice("), "export has a device-save path");
requireSource(html.includes('id="template-zoom-in"') && html.includes('id="template-zoom-out"') && html.includes("function bindTemplateCanvasZoom()"), "template canvas has working zoom controls and pan handlers");
requireSource(html.includes("position:sticky;top:82px") && html.includes(".canvas-wrap{order:-1;top:65px"), "template canvas stays at the top on mobile while scrolling layer controls");
requireSource(html.includes('data-cutout-mode="cutout"') && html.includes('data-cutout-mode="generate"') && html.includes('data-cutout-mode="blur"'), "project cutout offers transparent cutout, AI background, and bokeh blur modes");
requireSource(html.includes("fal-ai/bria/background/replace") && html.includes("makeBlurredBackgroundComposite(source,url") && html.includes("pf-cutout-background-prompt"), "AI background replacement and local bokeh modes are wired to project processing");

requireSource(html.includes("native-download.bundle.js"), "native download bridge is referenced");
// Service-worker cache invalidation is checked alongside the new brush regression assertions above.

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
let inlineSyntaxValid = false;
try {
  new Function(inlineScripts.join("\n;\n"));
  inlineSyntaxValid = true;
} catch (error) {
  console.error("[smoke-check] Inline application JavaScript syntax error:", error);
}
requireSource(inlineSyntaxValid, "inline application JavaScript parses without syntax errors");

if (process.exitCode) process.exit(process.exitCode);
console.log("[smoke-check] All static checks passed.");
