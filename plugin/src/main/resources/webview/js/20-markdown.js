  /* ---------- tiny offline syntax highlighter ---------- */
  const KW = new Set((
    'if else elseif for foreach while do switch case default break continue return yield ' +
    'function fn func def lambda class interface trait enum struct extends implements new ' +
    'public private protected static final abstract const let var val readonly ' +
    'void int float double string bool boolean object mixed array list map ' +
    'true false null nil none undefined this self super parent ' +
    'try catch finally throw throws and or not xor in is instanceof typeof as ' +
    'import from export default async await namespace use package module require include ' +
    'echo print global type struct range select insert update delete where join on group by ' +
    'order having union values into set with match when then end'
  ).split(' '));
  const HL_RE = /(\/\*[\s\S]*?\*\/|\/\/[^\n]*)|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`)|(\b\d[\d_.]*\b)|(\$[A-Za-z_]\w*)|(\b[A-Za-z_]\w*\b)/g;
  function highlight(code) {
    return code.replace(HL_RE, (m, cmt, str, num, varr, ident) => {
      if (cmt) return '<span class="hl-c">' + cmt + '</span>';
      if (str) return '<span class="hl-s">' + str + '</span>';
      if (num) return '<span class="hl-n">' + num + '</span>';
      if (varr) return '<span class="hl-v">' + varr + '</span>';
      if (ident) return KW.has(ident) ? '<span class="hl-k">' + ident + '</span>' : ident;
      return m;
    });
  }

  /* ---------- minimal, safe markdown renderer (canon markup) ---------- */
  function inlineMd(s) {
    // Backlog #34: a code span is OPAQUE to every pass below. Each is swapped for a token (U+0001
    // C<n> U+0001) and put back last — with the <code> written in place first, a `*` inside
    // `-*+` paired with a later `*` across the span and a URL inside one became a link. A span
    // is a backtick run closed by a run of the SAME length (CommonMark), so ``a ` b`` and a
    // one-line ```x``` are spans too; one space of padding on both sides is dropped.
    const codes = [];
    s = s.replace(/(?<!`)(`+)(?!`)([\s\S]*?[^`])\1(?!`)/g, function (m, run, c) {
      if (/^ [\s\S]* $/.test(c) && /\S/.test(c)) c = c.slice(1, -1);
      codes.push('<code class="ic">' + c + '</code>');
      return '\u0001C' + (codes.length - 1) + '\u0001';
    });
    s = s
      // Bold admits single `*` inside the pair (`**Garlic (*Allium sativum*)**`), stopping at the
      // first `**`; the italic pass below then pairs the inner asterisks with the <b> already in
      // place. With `[^*]+` here the bold never matched and the user saw the `**` printed
      // around an italic species name (2026-10-10, fixture 90).
      .replace(/\*\*((?:[^*]|\*(?!\*))+)\*\*/g, '<b>$1</b>')
      .replace(/(^|[^*])\*([^*]+)\*/g, '$1<i>$2</i>')
      .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, '<a href="$2">$1</a>')
      // bare URLs autolink too. Not one preceded by `"` or `>` — that is the href, or the text, of
      // a link the line above just made. Trailing sentence punctuation stays outside the link.
      .replace(/(^|[^"'>\w])(https?:\/\/[^\s<\u0001]*[^\s<\u0001.,;:!?)\]'"])/g, '$1<a href="$2">$2</a>');
    return codes.length ? s.replace(/\u0001C(\d+)\u0001/g, function (m, n) { return codes[+n]; }) : s;
  }
  // No target="_blank" on any of these (removed 2026-08-29): the panel is an OFF-SCREEN JCEF
  // browser, and a _blank click asked CEF for a popup window, which under OSR has no surface to
  // draw on — the user saw blank PhpStorm windows. External links now go to the system browser
  // through the document-level click delegate in 00-core.js (`browse` bridge frame), with
  // ChatPanel's onBeforePopup as the catch-all for anything else that would spawn a popup.
  // A quote marker reaches the block parser as `&gt;`, never `>`, because esc() runs FIRST — see
  // MD_QUOTE. Testing for the raw character is why the blockquote branch silently never fired
  // until 2026-08-03: quoted lines fell through to the paragraph branch, printing the marker
  // literally, and a `- item` behind that marker no longer started the line so the list branch
  // missed it too. Any NEW block rule added here has to match escaped text for the same reason.
  const MD_QUOTE = /^\s*&gt;\s?/;
  /* GFM tables. Split a row into cells: outer pipes are optional (both forms are legal GFM),
     and a `\|` inside a cell is an escaped literal, not a separator. */
  function mdCells(line) {
    let s = line.trim();
    if (s.charAt(0) === '|') s = s.slice(1);
    if (s.slice(-1) === '|' && s.slice(-2) !== '\\|') s = s.slice(0, -1);
    return s.split(/(?<!\\)\|/).map(function (c) { return c.trim().replace(/\\\|/g, '|'); });
  }
  /* A table is recognised by its DELIMITER row, never by the header — that is what keeps a lone
     `---` an <hr> and stops an ordinary paragraph that happens to contain a `|` from being eaten. */
  function mdIsSep(line) {
    return line.indexOf('|') >= 0 && mdCells(line).every(function (c) { return /^:?-+:?$/.test(c); });
  }
  function mdTableAt(lines, i) {
    return lines[i].indexOf('|') >= 0 && i + 1 < lines.length && mdIsSep(lines[i + 1]);
  }
  function renderMd(srcRaw) { return mdBlocks(esc(srcRaw), []); }
  /* ---------- math (1.34): KaTeX, the page's one vendored library ----------
     `$$…$$` and `\[…\]` are display math, `\(…\)` and `$…$` inline. Found by ONE pass over the
     block text after fenced code has been lifted out; the same regex matches inline code spans
     first and returns them untouched, so a `$` inside backticks is never math. Each formula is
     swapped for a token (U+0001 M<n> U+0001) that no markdown rule can touch — a `|` or `*`
     inside a formula would otherwise split a table cell or open an italic — and the tokens are
     put back as KaTeX's HTML once the blocks are built.
     `$…$` is the ambiguous one, so it is strict (pandoc's rule): one line, no space just inside
     either `$`, the opener not after a word character, a backslash or another `$`, the closer not
     before a digit, and no backtick inside. "$5 and $10" and "$5-$10" stay text, and a price
     cannot pair with a `$` inside a later code span ("$10, and `$x$`" did, in the first build's
     own screenshot — the code-span alternative only protects a span the scan REACHES).
     Left as source text: a formula KaTeX cannot parse, any formula while it is still streaming
     in (no closer yet), and everything when the library is absent (window.katex undefined).
     The text reaching this pass is ALREADY ESCAPED (esc() runs first), so the TeX is unescaped
     for KaTeX and the fallback returns the escaped original. KaTeX's own output is safe to
     insert: `trust` stays off, so \href / \includegraphics / \htmlClass are refused.
     A reply re-renders whole on every streamed chunk, so rendered formulas are cached by source. */
  const MATH_RE = /(`[^`\n]+`)|\$\$([\s\S]+?)\$\$|\\\[([\s\S]+?)\\\]|\\\((.+?)\\\)|(?<![\\$\w])\$(?!\s)([^$\n`]+?)(?<![\s\\])\$(?![\d$])/g;
  const mathCache = new Map();
  function mathHtml(tex, display) {
    if (typeof katex === 'undefined' || !katex.renderToString) return null;
    const key = (display ? 'D' : 'I') + tex;
    if (mathCache.has(key)) return mathCache.get(key);
    let html = null;
    try {
      const raw = tex.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&');
      html = '<span class="math' + (display ? ' math-d' : '') + '">' +
        katex.renderToString(raw, { displayMode: display, throwOnError: true }) + '</span>';
    } catch (e) { html = null; }
    if (mathCache.size > 400) mathCache.clear();
    mathCache.set(key, html);
    return html;
  }
  function mathOut(src, store) {
    if (src.indexOf('$') < 0 && src.indexOf('\\') < 0) return src;
    return src.replace(MATH_RE, function (m, code, dd, db, ip, id) {
      if (code) return m;
      const display = dd != null || db != null;
      const tex = dd != null ? dd : db != null ? db : ip != null ? ip : id;
      if (!tex || !tex.trim()) return m;
      const html = mathHtml(tex.trim(), display);
      if (html == null) return m;
      store.push(html);
      return '\u0001M' + (store.length - 1) + '\u0001';
    });
  }
  /* List item marker (CommonMark): indent, `-`/`*`/`+` or 1-9 digits + `.`/`)`, then 1-4 spaces
     (or nothing). The indent is unbounded on purpose — the renderer has no indented-code block,
     so a bullet four spaces in is still a bullet, not code. Groups: indent, marker, gap, text. */
  const LI_RE = /^( *)([-*+]|\d{1,9}[.)])( {1,4}|$)(.*)$/;
  // A lifted code block's placeholder, alone on its line: U+0001 B<n> U+0001 (backlog #35 — it
  // was the printable " B<n> ", which a line holding anything else printed as "B0").
  const CB_LINE = /^ *\u0001B(\d+)\u0001 *$/;
  function codeBlockHtml(lang, code) {
    return '<div class="codeblock"><div class="cb-h"><span>' + esc(lang || 'code') +
      '</span><button class="copy" title="Copy">' + SVG_COPY + '</button></div><pre>' +
      highlight(code.replace(/\n[ \t]*$/, '')) + '</pre></div>';
  }
  // A line that would interrupt a paragraph (and so ends a list item's lazy continuation).
  function mdBlockStart(lines, i) {
    const l = lines[i];
    return CB_LINE.test(l) || /^(#{1,6})\s/.test(l) || /^\s*(-{3,}|\*{3,})\s*$/.test(l) ||
      MD_QUOTE.test(l) || LI_RE.test(l) || mdTableAt(lines, i);
  }
  /**
   * One list starting at lines[i] (which matches LI_RE). Returns {html, i} with i past the list.
   * CommonMark structure, which is what stops numbered lists restarting at `1.` (the user saw it
   * "so many times", 2026-09-09): the OLD branches took consecutive marker lines only, so a
   * blank line between items, a wrapped line indented under its item, a nested bullet or an
   * indented fence all ended the list, and the next marker opened a fresh <ol> with no `start`.
   * Rules: an item owns every following line that is blank or indented at least W (its content
   * indent = indent + marker + gap), plus LAZY lines (unindented text directly after a non-blank
   * line that is not a block start). A blank line followed by a sibling marker, or between an
   * item's own blocks, makes the list LOOSE (items keep their <p>); otherwise it is tight and
   * item text is emitted bare. Item bodies re-enter mdBlocks, so nested lists, tables and fence
   * placeholders inside items come for free. Numbering follows the first marker (`start`), and
   * `1. 1. 1.` counts up because the browser numbers the <li>s. A bullet-character change does
   * NOT split a list (more forgiving than CommonMark; the model is consistent within one list).
   */
  function mdList(lines, i, blocks) {
    const head = lines[i].match(LI_RE);
    const ordered = /\d/.test(head[2]);
    const items = [];
    let loose = false;
    while (i < lines.length) {
      const m = lines[i].match(LI_RE);
      if (!m || /\d/.test(m[2]) !== ordered) break;
      const W = m[1].length + m[2].length + (m[3].length || 1);
      const item = [m[4]];
      i++;
      let blanks = 0;
      while (i < lines.length) {
        const l = lines[i];
        if (/^\s*$/.test(l)) { blanks++; i++; continue; }
        const ind = l.match(/^ */)[0].length;
        if (ind >= W) {
          if (blanks) { loose = true; item.push(''); blanks = 0; }
          item.push(l.slice(W)); i++; continue;
        }
        if (blanks || mdBlockStart(lines, i)) break;
        item.push(l); i++;                       // lazy continuation
      }
      items.push(item);
      if (blanks && i < lines.length) {
        const n = lines[i].match(LI_RE);
        if (n && /\d/.test(n[2]) === ordered) loose = true;
      }
    }
    const tag = ordered ? 'ol' : 'ul';
    const start = ordered ? parseInt(head[2], 10) : 1;
    const attr = ordered && start !== 1 ? ' start="' + start + '"' : '';
    return { i: i, html: '<' + tag + attr + '>' + items.map(function (it) {
      return '<li>' + mdBlocks(it.join('\n'), blocks, !loose) + '</li>';
    }).join('') + '</' + tag + '>' };
  }
  /**
   * Block parser over ALREADY-ESCAPED markdown. Split out of renderMd so a blockquote can
   * re-enter it for its own contents — a quoted list stays a list instead of collapsing into one
   * run-on paragraph — without escaping the text a second time. `blocks` (fenced-code
   * placeholders) is shared with nested calls so a placeholder made by the outer pass still
   * resolves when a nested one emits it. `tight` (a tight list item's body) emits paragraphs
   * bare, without <p>, the way CommonMark renders them.
   */
  function mdBlocks(src, blocks, tight) {
    // [^\s`]* not \w*: a language can carry punctuation, and \w* left the remainder in the body
    // (```c++ parsed as lang "c" + body "++\n…"). Unlabelled fences read as "code" so the header
    // is never an empty strip with a lone copy button.
    // A fence is a run of THREE OR MORE backticks and closes only on a run at least as long
    // (CommonMark; the extra backticks of a longer closer are eaten by the trailing `*). A fixed
    // ``` used to split a ````markdown fence at its first three backticks: lang read "", the body
    // began with the stray fourth backtick, the closer matched three of its four, and the one
    // left behind made " B0 `" — no longer a whole-line placeholder — print literally while the
    // block (a whole table) vanished (user sighting 2026-09-08; fixture 85).
    // An indented fence (under a list item) drops up to its own indent from every body line, as
    // CommonMark does; the indent itself stays in front of the placeholder so the list parser
    // can claim the line for its item. The trailing strip also eats an indented closer's spaces.
    // Backlog #35: `~~~` is a fence too, at a line start only (a run of tildes in running text
    // is not one). A match with no line break in it is not a fence at all — ```x``` on one line
    // is a code span, left for inlineMd. A fence opened MID-LINE ("Run this: ```sh") is still
    // lifted: its placeholder goes on a line of its own, indented under the text that led into
    // it so a list item keeps it, and text after the closer drops to the next line. Before, the
    // placeholder sat inside the line, was never restored, and the block vanished behind "B0".
    src = src.replace(/(`{3,}|~{3,})([^\s`]*)\n?([\s\S]*?)\1[`~]*/g, (m, fence, lang, code, offset, str) => {   // fenced code
      if (m.indexOf('\n') < 0) return m;
      const pre = str.slice(str.lastIndexOf('\n', offset - 1) + 1, offset);
      const atStart = /^ *$/.test(pre);
      if (fence.charAt(0) === '~' && !atStart) return m;
      // Straight after quote markers ("> ```") the fence stays where it is — the quote branch
      // strips the markers and finds the placeholder — and its body loses the markers too.
      const quoted = !atStart && /^(\s*&gt;\s?)+$/.test(pre);
      const li = atStart || quoted ? null : pre.match(LI_RE);
      const ind = li ? li[1].length + li[2].length + (li[3].length || 1) : pre.match(/^ */)[0].length;
      if (atStart && pre) code = code.replace(new RegExp('^ {1,' + pre.length + '}', 'gm'), '');
      else if (quoted) code = code.replace(/^(\s*&gt;\s?)+/gm, '');
      else if (li) code = code.replace(new RegExp('^ {1,' + ind + '}', 'gm'), '');   // "- run: ```"
      blocks.push(codeBlockHtml(lang, code));
      const token = '\u0001B' + (blocks.length - 1) + '\u0001';
      const after = str.slice(offset + m.length);
      const tail = /^[^\S\n]*\S/.test(after) ? '\n' : '';
      if (atStart || quoted) return token + tail;
      return '\n' + ' '.repeat(ind) + token + tail;
    });
    // 1.34: math out, once, on the outermost call (nested calls — a quote, a list item — get text
    // that already carries the tokens). `blocks.math` is the token store shared with them.
    const mathTop = !blocks.math;
    if (mathTop) { blocks.math = []; src = mathOut(src, blocks.math); }
    const lines = src.split('\n');
    let out = '', i = 0;
    while (i < lines.length) {
      let line = lines[i];
      const ph = line.match(CB_LINE);
      if (ph) { out += blocks[+ph[1]]; i++; continue; }
      if (/^\s*$/.test(line)) { i++; continue; }
      let m;
      // Backlog #33: indented code (CommonMark) — a block that STARTS four or more spaces in is
      // code, through to the last such line; blank lines inside it stay. It cannot interrupt a
      // paragraph (the paragraph branch below takes a deeper line as continuation), and inside a
      // list item the four spaces count from the item's own content column, which mdList has
      // already cut away. A list marker is still a list however deep (LI_RE's note): models
      // indent nested bullets by four all the time.
      if (/^ {4,}\S/.test(line) && !LI_RE.test(line)) {
        const buf = [];
        while (i < lines.length) {
          if (/^ {4,}\S/.test(lines[i]) && !CB_LINE.test(lines[i])) { buf.push(lines[i].slice(4)); i++; continue; }
          let j = i;
          while (j < lines.length && /^\s*$/.test(lines[j])) j++;
          if (j > i && j < lines.length && /^ {4,}\S/.test(lines[j]) && !LI_RE.test(lines[j]) && !CB_LINE.test(lines[j])) {
            while (i < j) { buf.push(''); i++; }
            continue;
          }
          break;
        }
        out += codeBlockHtml('', buf.join('\n'));
        continue;
      }
      if ((m = line.match(/^(#{1,6})\s+(.*)$/))) { out += '<h' + m[1].length + '>' + inlineMd(m[2]) + '</h' + m[1].length + '>'; i++; continue; }
      if (/^\s*(-{3,}|\*{3,})\s*$/.test(line)) { out += '<hr>'; i++; continue; }
      if (MD_QUOTE.test(line)) {
        let buf = [];
        while (i < lines.length && MD_QUOTE.test(lines[i])) { buf.push(lines[i].replace(MD_QUOTE, '')); i++; }
        out += '<blockquote>' + mdBlocks(buf.join('\n'), blocks) + '</blockquote>'; continue;
      }
      if (mdTableAt(lines, i)) {
        const heads = mdCells(line);
        // ':' on a side of the delimiter cell sets that column's alignment for header AND body
        const al = mdCells(lines[i + 1]).map(function (c) {
          return c.slice(-1) === ':' ? (c.charAt(0) === ':' ? ' class="ta-c"' : ' class="ta-r"') : '';
        });
        i += 2;
        let rows = '';
        // Body rows are padded/truncated to the header's column count, as GFM requires — a ragged
        // row must not shift the columns of the rows below it.
        while (i < lines.length && lines[i].indexOf('|') >= 0 && !mdIsSep(lines[i])) {
          const cells = mdCells(lines[i]); i++;
          rows += '<tr>' + heads.map(function (_, c) {
            return '<td' + (al[c] || '') + '>' + inlineMd(cells[c] || '') + '</td>';
          }).join('') + '</tr>';
        }
        out += '<div class="tbl"><table><thead><tr>' + heads.map(function (h, c) {
          return '<th' + (al[c] || '') + '>' + inlineMd(h) + '</th>';
        }).join('') + '</tr></thead>' + (rows ? '<tbody>' + rows + '</tbody>' : '') + '</table></div>';
        continue;
      }
      if (LI_RE.test(line)) {
        const lst = mdList(lines, i, blocks);
        out += lst.html; i = lst.i; continue;
      }
      // paragraph: gather consecutive normal lines
      let buf = [];
      while (i < lines.length && !/^\s*$/.test(lines[i]) && !mdBlockStart(lines, i)) {
        buf.push(lines[i]); i++;
      }
      const para = inlineMd(buf.join('\n')).replace(/\n/g, '<br>');
      out += tight ? para : '<p>' + para + '</p>';
    }
    if (mathTop && blocks.math.length) out = out.replace(/\u0001M(\d+)\u0001/g, function (m, n) { return blocks.math[+n] || ''; });
    return out;
  }

  // ONE clipboard route for the page: Kotlin's CopyPasteManager (ChatPanel "copy"). Not
  // navigator.clipboard — it needs a focused document and a user activation, which the webview
  // does not always hold (a programmatic click rejected the write, 2026-10-10), and it cannot be
  // read back by the harness; the IDE's clipboard can (xclip).
  function copyText(text) { bridge({ kind: 'copy', text: String(text == null ? '' : text) }); }
  // The same route for a picture (ChatPanel "copyImage"): redrawn on a canvas first so Kotlin only
  // ever decodes PNG — a tool can return JPEG, GIF or WebP, and Java's ImageIO cannot read WebP.
  // `btn` flashes to the check once the picture has gone to the bridge, not on the click: the tick
  // means "sent", and a picture the page cannot decode shows no tick at all.
  function copyImage(src, btn) {
    const im = new Image();
    im.onload = function () {
      const c = document.createElement('canvas');
      c.width = im.naturalWidth; c.height = im.naturalHeight;
      c.getContext('2d').drawImage(im, 0, 0);
      bridge({ kind: 'copyImage', data: c.toDataURL('image/png').split(',')[1] });
      if (btn) flashCopied(btn);
    };
    im.src = src;
  }
  // the copy controls' shared feedback: the glyph flashes to the check
  function flashCopied(btn) {
    btn.innerHTML = SVG_CHECK;
    setTimeout(function () { btn.innerHTML = SVG_COPY; }, 1200);
  }
  // delegate copy-button clicks (codeblock header icon)
  log.addEventListener('click', (e) => {
    const btn = e.target.closest('.copy');
    if (!btn) return;
    const pre = btn.closest('.codeblock').querySelector('pre');
    if (pre) copyText(pre.textContent);
    flashCopied(btn);
  });

  /* Copy response (15.4). mdBlock paints a FINAL assistant text block in one step: render, keep
     the markdown SOURCE on the element, fold long code, add the copy control. The live path paints
     the same element many times while streaming (flushMd) and calls copyable() only once the block
     is final — the control is inserted after that because an innerHTML assignment wipes it; it goes
     FIRST so the CSS float sits at the first line (css/30-blocks.css). Copying the
     source rather than textContent is the reference client's behaviour too (its button copies the
     message's text content, markdown and all). The side panel's answers are .blk as well but are
     not responses, so they never go through here. */
  function mdBlock(k, src) { k.innerHTML = renderMd(src); k.__md = src; foldCode(k); copyable(k); return k; }

  /* ---------- model switch between the steps of one task ----------
     Claude ends a step with a marker as the LAST line of its reply (the protocol the plugin
     appends to the system prompt, resources/handoff/protocol.md):
         <<MODEL_SWITCH v="1" model="opus" next="step-2">>
     The panel hides that line, switches the model and sends the continue message (startHandoff,
     80-gauge.js). Only the last line counts: marker syntax quoted mid-message is ordinary text.
     Nothing is handed over: it is the same conversation, so the next model reads the plan and
     the earlier steps from it (user, 2026-11-25 — the first build's ./.handoff/state.md file
     cost a Read, a Write and a permission card per step and told the model nothing new).
     MEASURED 2026-11-25 on 2.1.331 over stdio (sonnet → haiku → sonnet): the marker was the
     reply's last line every time, with no trailing whitespace, and no file was written. */
  const HANDOFF_RE = /^<<MODEL_SWITCH v="1" model="([^"\n]{1,40})" next="([\w-]{1,60})">>$/;
  const HANDOFF_HEAD = '<<MODEL_SWITCH';
  const HANDOFF_MODELS = ['fable', 'opus', 'sonnet', 'haiku'];
  function handoffTail(src) {
    const s = String(src == null ? '' : src).replace(/\s+$/, ''), i = s.lastIndexOf('\n');
    return { head: i < 0 ? '' : s.slice(0, i).replace(/\s+$/, ''), last: s.slice(i + 1) };
  }
  // A finished text: { text: what to draw, mark: {model, next} | null }.
  function handoffSplit(src) {
    const t = handoffTail(src), m = HANDOFF_RE.exec(t.last);
    return m ? { text: t.head, mark: { model: m[1], next: m[2] } } : { text: src, mark: null };
  }
  // A text still streaming: the marker, or its beginning, is not drawn while it is the last line
  // (a line that turns out not to be a marker comes back with the next delta or at the block's end).
  function handoffHideTail(src) {
    const t = handoffTail(src);
    return t.last.length >= 2 && (HANDOFF_HEAD.indexOf(t.last) === 0 || t.last.indexOf(HANDOFF_HEAD) === 0) ? t.head : src;
  }
  // An assistant text block, live un-streamed and replay: the marker line is cut, the mark kept on
  // the element. A reply that was the marker alone leaves nothing to draw.
  function asstBlock(k, src) {
    const h = handoffSplit(src);
    mdBlock(k, h.text);
    if (h.mark) { k.__handoff = h.mark; k.hidden = !String(h.text).trim(); }
    return k;
  }
  // The line both live and replay draw where a switch happened.
  function handoffLine(mark) {
    return statusLine('Switch to ' + chipLabelFor(mark.model) + ' \u00b7 ' + mark.next, SVG_SPARK, 'status handoff');
  }
  function copyable(k) {
    if (!k || k.querySelector(':scope > .blk-copy')) return;
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'blk-copy'; b.title = 'Copy response';
    b.innerHTML = SVG_COPY;
    k.insertBefore(b, k.firstChild);
    // 8.15: its bookmark twin, right after it (copy keeps the rightmost float); hidden by css
    // until the block carries a uuid, filled by bmMarkOne once it does and is bookmarked
    const m = document.createElement('button');
    m.type = 'button'; m.className = 'blk-mark'; m.title = 'Bookmark response';
    m.innerHTML = SVG_BM;
    k.insertBefore(m, b.nextSibling);
  }
  log.addEventListener('click', function (e) {
    const btn = e.target.closest('.blk-copy');
    if (!btn) return;
    e.stopPropagation();
    const k = btn.parentNode;
    // the source when the block kept it (every assistant text block does), else what is on screen
    copyText(typeof k.__md === 'string' ? k.__md : k.textContent);
    flashCopied(btn);
  });

