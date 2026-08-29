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
    return s
      .replace(/`([^`]+)`/g, (m, c) => '<code class="ic">' + c + '</code>')
      .replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>')
      .replace(/(^|[^*])\*([^*]+)\*/g, '$1<i>$2</i>')
      .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, '<a href="$2">$1</a>')
      // bare URLs autolink too. Not one preceded by `"` or `>` — that is the href, or the text, of
      // a link the line above just made. Trailing sentence punctuation stays outside the link.
      .replace(/(^|[^"'>\w])(https?:\/\/[^\s<]*[^\s<.,;:!?)\]'"])/g, '$1<a href="$2">$2</a>');
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
  /**
   * Block parser over ALREADY-ESCAPED markdown. Split out of renderMd so a blockquote can
   * re-enter it for its own contents — a quoted list stays a list instead of collapsing into one
   * run-on paragraph — without escaping the text a second time. `blocks` (fenced-code
   * placeholders) is shared with nested calls so a placeholder made by the outer pass still
   * resolves when a nested one emits it.
   */
  function mdBlocks(src, blocks) {
    // [^\s`]* not \w*: a language can carry punctuation, and \w* left the remainder in the body
    // (```c++ parsed as lang "c" + body "++\n…"). Unlabelled fences read as "code" so the header
    // is never an empty strip with a lone copy button.
    src = src.replace(/```([^\s`]*)\n?([\s\S]*?)```/g, (m, lang, code) => {   // fenced code
      blocks.push('<div class="codeblock"><div class="cb-h"><span>' + esc(lang || 'code') +
        '</span><button class="copy" title="Copy">' + SVG_COPY + '</button></div><pre>' +
        highlight(code.replace(/\n$/, '')) + '</pre></div>');
      return ' B' + (blocks.length - 1) + ' ';
    });
    const lines = src.split('\n');
    let out = '', i = 0;
    while (i < lines.length) {
      let line = lines[i];
      const ph = line.match(/^ B(\d+) $/);
      if (ph) { out += blocks[+ph[1]]; i++; continue; }
      if (/^\s*$/.test(line)) { i++; continue; }
      let m;
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
      if (/^\s*([-*+])\s+/.test(line)) {
        let buf = [];
        while (i < lines.length && /^\s*([-*+])\s+/.test(lines[i])) { buf.push('<li>' + inlineMd(lines[i].replace(/^\s*([-*+])\s+/, '')) + '</li>'); i++; }
        out += '<ul>' + buf.join('') + '</ul>'; continue;
      }
      if (/^\s*\d+\.\s+/.test(line)) {
        let buf = [];
        while (i < lines.length && /^\s*\d+\.\s+/.test(lines[i])) { buf.push('<li>' + inlineMd(lines[i].replace(/^\s*\d+\.\s+/, '')) + '</li>'); i++; }
        out += '<ol>' + buf.join('') + '</ol>'; continue;
      }
      // paragraph: gather consecutive normal lines
      let buf = [];
      while (i < lines.length && !/^\s*$/.test(lines[i]) && !/^ B\d+ $/.test(lines[i]) &&
             !/^(#{1,6})\s/.test(lines[i]) && !/^\s*([-*+]|\d+\.)\s/.test(lines[i]) && !MD_QUOTE.test(lines[i]) &&
             !mdTableAt(lines, i)) {
        buf.push(lines[i]); i++;
      }
      out += '<p>' + inlineMd(buf.join('\n')).replace(/\n/g, '<br>') + '</p>';
    }
    return out;
  }

  // delegate copy-button clicks (codeblock header icon)
  log.addEventListener('click', (e) => {
    const btn = e.target.closest('.copy');
    if (!btn) return;
    const pre = btn.closest('.codeblock').querySelector('pre');
    if (navigator.clipboard && pre) navigator.clipboard.writeText(pre.textContent);
    btn.innerHTML = SVG_CHECK;
    setTimeout(() => { btn.innerHTML = SVG_COPY; }, 1200);
  });

