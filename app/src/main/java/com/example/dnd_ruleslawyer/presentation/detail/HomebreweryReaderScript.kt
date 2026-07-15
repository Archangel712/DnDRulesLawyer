package com.example.dnd_ruleslawyer.presentation.detail

object HomebreweryReaderScript {
    val EXTRACT_RENDERED_CONTENT = """
        (function() {
            var READY_CLASS = 'dnd-ruleslawyer-homebrewery-reader-ready';
            var READER_CLASS = 'dnd-ruleslawyer-homebrewery-reader';
            var PAGE_SELECTOR = '.page, .phb';
            var PAGES_SELECTOR = '.pages, #pages';

            if (document.documentElement.classList.contains(READY_CLASS)) {
                return 'ok-already';
            }

            function visibleTextLength(element) {
                if (!element || !element.innerText) return 0;
                return element.innerText.trim().length;
            }

            function pageCount(element) {
                if (!element) return 0;
                if (element.matches && element.matches(PAGE_SELECTOR)) return 1;
                return element.querySelectorAll(PAGE_SELECTOR).length;
            }

            function candidateScore(element) {
                return (meaningfulPageCount(element) * 5000) + visibleTextLength(element);
            }

            function isLoadingOnly(element) {
                if (!element) return true;

                var text = element.innerText ? element.innerText.trim().toLowerCase() : '';
                var spinner = element.querySelector(
                    '.fa-spinner, .fa-circle-o-notch, .spinner, .loading, [class*="spinner"], [class*="loading"]'
                );

                if (spinner && text.length < 40) return true;
                return text === 'loading' || text === 'loading...' || text === 'rendering';
            }

            function meaningfulPageCount(element) {
                if (!element) return 0;

                var pages = [];

                if (element.matches && element.matches(PAGE_SELECTOR)) {
                    pages.push(element);
                }

                Array.prototype.push.apply(pages, element.querySelectorAll(PAGE_SELECTOR));

                return Array.prototype.filter.call(pages, function(page) {
                    return !isLoadingOnly(page) && visibleTextLength(page) >= 40;
                }).length;
            }

            function hasRenderedContent(element) {
                return !!element && !isLoadingOnly(element) && (
                    meaningfulPageCount(element) > 0 || visibleTextLength(element) >= 120
                );
            }

            function absolutizeResourceAttributes(element, sourceDocument) {
                var baseUrl = sourceDocument.location && sourceDocument.location.href !== 'about:blank'
                    ? sourceDocument.location.href
                    : document.location.href;

                Array.prototype.forEach.call(element.querySelectorAll('[src], [href]'), function(node) {
                    ['src', 'href'].forEach(function(attribute) {
                        var value = node.getAttribute(attribute);
                        if (!value || value.charAt(0) === '#' || value.indexOf('javascript:') === 0) return;

                        try {
                            node.setAttribute(attribute, new URL(value, baseUrl).href);
                        } catch (error) {
                            // Keep the original value if the browser cannot resolve it.
                        }
                    });
                });
            }

            function copyStylesFrom(sourceDocument) {
                if (!sourceDocument || !sourceDocument.head) return;

                Array.prototype.forEach.call(
                    document.querySelectorAll('[data-dnd-ruleslawyer-frame-style="true"]'),
                    function(node) { node.remove(); }
                );

                Array.prototype.forEach.call(
                    sourceDocument.head.querySelectorAll('link[rel~="stylesheet"], style'),
                    function(sourceNode) {
                        var copiedNode;

                        if (sourceNode.tagName && sourceNode.tagName.toLowerCase() === 'link') {
                            var href = sourceNode.href || sourceNode.getAttribute('href');
                            if (!href) return;

                            copiedNode = document.createElement('link');
                            copiedNode.rel = 'stylesheet';
                            copiedNode.href = href;

                            if (sourceNode.media) copiedNode.media = sourceNode.media;
                            if (sourceNode.crossOrigin) copiedNode.crossOrigin = sourceNode.crossOrigin;
                        } else {
                            copiedNode = document.createElement('style');
                            copiedNode.textContent = sourceNode.textContent || '';
                        }

                        copiedNode.setAttribute('data-dnd-ruleslawyer-frame-style', 'true');
                        document.head.appendChild(copiedNode);
                    }
                );
            }

            function importPages(sourceDocument, element) {
                var clone = document.importNode(element, true);
                absolutizeResourceAttributes(clone, sourceDocument);

                if (!clone.classList.contains(READER_CLASS)) {
                    var outer = document.createElement('main');
                    outer.className = READER_CLASS;
                    outer.appendChild(clone);
                    clone = outer;
                }

                return clone;
            }

            function bestActualPageContainer(sourceDocument) {
                var candidates = [];

                Array.prototype.forEach.call(sourceDocument.querySelectorAll(PAGES_SELECTOR), function(element) {
                    if (hasRenderedContent(element)) {
                        candidates.push(element);
                    }
                });

                if (candidates.length === 0) {
                    var pages = Array.prototype.filter.call(
                        sourceDocument.querySelectorAll(PAGE_SELECTOR),
                        hasRenderedContent
                    );

                    if (pages.length > 0) {
                        var wrapper = sourceDocument.createElement('main');
                        wrapper.className = 'pages';

                        pages.forEach(function(page) {
                            wrapper.appendChild(page.cloneNode(true));
                        });

                        candidates.push(wrapper);
                    }
                }

                candidates.sort(function(a, b) {
                    return candidateScore(b) - candidateScore(a);
                });

                return candidates[0] || null;
            }

            function extractFromBrewRendererFrame() {
                var frames = Array.prototype.slice.call(
                    document.querySelectorAll('iframe#BrewRenderer, iframe[name="BrewRenderer"], iframe')
                );

                for (var index = 0; index < frames.length; index++) {
                    var frame = frames[index];
                    var frameDocument = null;

                    try {
                        frameDocument = frame.contentDocument || (frame.contentWindow && frame.contentWindow.document);
                    } catch (error) {
                        frameDocument = null;
                    }

                    if (!frameDocument || !frameDocument.body) continue;

                    var pages = bestActualPageContainer(frameDocument);
                    if (pages) {
                        return {
                            frame: frame,
                            clone: null,
                            sourceDocument: frameDocument,
                            source: 'iframe-live'
                        };
                    }
                }

                return null;
            }

            function extractFromOuterDocument() {
                var pages = bestActualPageContainer(document);
                if (!pages) return null;

                return {
                    clone: importPages(document, pages),
                    sourceDocument: document,
                    source: 'outer'
                };
            }

            function fitFrameToRenderedContent(frame) {
                var frameDocument = null;

                try {
                    frameDocument = frame.contentDocument || (frame.contentWindow && frame.contentWindow.document);
                } catch (error) {
                    frameDocument = null;
                }

                if (!frameDocument) return;

                var body = frameDocument.body;
                var html = frameDocument.documentElement;
                var height = Math.max(
                    body ? body.scrollHeight : 0,
                    body ? body.offsetHeight : 0,
                    html ? html.clientHeight : 0,
                    html ? html.scrollHeight : 0,
                    html ? html.offsetHeight : 0,
                    window.innerHeight || 0
                );

                frame.style.height = Math.max(height, window.innerHeight || 0) + 'px';
            }

            function setImportantStyle(element, property, value) {
                element.style.setProperty(property, value, 'important');
            }

            function keepFrameInPlace(frame) {
                var path = [];
                var node = frame;

                while (node && node !== document.body) {
                    path.push(node);
                    node = node.parentElement;
                }

                if (document.body) {
                    path.push(document.body);
                }

                if (document.documentElement) {
                    setImportantStyle(document.documentElement, 'margin', '0');
                    setImportantStyle(document.documentElement, 'padding', '0');
                    setImportantStyle(document.documentElement, 'background', '#ece4cf');
                    setImportantStyle(document.documentElement, 'overflow', 'auto');
                }

                for (var pathIndex = path.length - 1; pathIndex > 0; pathIndex--) {
                    var ancestor = path[pathIndex];
                    var childOnPath = path[pathIndex - 1];

                    Array.prototype.forEach.call(ancestor.children, function(child) {
                        if (child !== childOnPath) {
                            setImportantStyle(child, 'display', 'none');
                        }
                    });
                }

                path.forEach(function(element) {
                    element.setAttribute('data-dnd-ruleslawyer-reader-keep', 'true');
                    setImportantStyle(element, 'display', 'block');
                    setImportantStyle(element, 'visibility', 'visible');
                    setImportantStyle(element, 'opacity', '1');
                    setImportantStyle(element, 'margin', '0');
                    setImportantStyle(element, 'padding', '0');
                    setImportantStyle(element, 'border', '0');
                    setImportantStyle(element, 'background', '#ece4cf');
                    setImportantStyle(element, 'min-height', '100vh');
                    setImportantStyle(element, 'overflow', 'visible');
                });

                frame.setAttribute('data-dnd-ruleslawyer-reader-frame', 'true');
                setImportantStyle(frame, 'display', 'block');
                setImportantStyle(frame, 'visibility', 'visible');
                setImportantStyle(frame, 'opacity', '1');
                setImportantStyle(frame, 'width', '100vw');
                setImportantStyle(frame, 'min-height', '100vh');
                setImportantStyle(frame, 'margin', '0');
                setImportantStyle(frame, 'padding', '0');
                setImportantStyle(frame, 'border', '0');
                setImportantStyle(frame, 'background', '#ece4cf');
            }

            function installLiveFrameReader(frame) {
                var viewport = document.querySelector('meta[name="viewport"]');
                if (!viewport) {
                    viewport = document.createElement('meta');
                    viewport.setAttribute('name', 'viewport');
                    document.head.appendChild(viewport);
                }
                viewport.setAttribute('content', 'width=device-width, initial-scale=1, viewport-fit=cover');

                var style = document.createElement('style');
                style.setAttribute('data-dnd-ruleslawyer-reader', 'true');
                style.textContent = [
                    'html, body { min-height: 100%; margin: 0 !important; padding: 0 !important; background: #ece4cf !important; overflow: auto !important; }',
                    '[data-dnd-ruleslawyer-reader-keep="true"] { display: block !important; visibility: visible !important; opacity: 1 !important; margin: 0 !important; padding: 0 !important; border: 0 !important; background: #ece4cf !important; overflow: visible !important; }',
                    'iframe[data-dnd-ruleslawyer-reader-frame="true"] { display: block !important; visibility: visible !important; opacity: 1 !important; width: 100vw !important; min-height: 100vh !important; margin: 0 !important; padding: 0 !important; border: 0 !important; background: #ece4cf !important; }'
                ].join('\n');
                document.head.appendChild(style);

                keepFrameInPlace(frame);
                frame.setAttribute('scrolling', 'auto');

                fitFrameToRenderedContent(frame);
                setTimeout(function() { fitFrameToRenderedContent(frame); }, 250);
                setTimeout(function() { fitFrameToRenderedContent(frame); }, 1000);
                window.addEventListener('resize', function() {
                    fitFrameToRenderedContent(frame);
                });

                document.documentElement.classList.add(READY_CLASS);
                window.scrollTo(0, 0);

                return 'ok-iframe-live';
            }

            function stripReaderChrome() {
                var chromeSelectors = [
                    'nav',
                    'header',
                    '.navbar',
                    '.navBar',
                    '.headerNav',
                    '.toolBar',
                    '.toolbar',
                    '.brewRendererControls',
                    '.popups',
                    '.modal',
                    '.notification'
                ];

                chromeSelectors.forEach(function(selector) {
                    Array.prototype.forEach.call(document.querySelectorAll(selector), function(element) {
                        if (!element.closest('.' + READER_CLASS)) {
                            element.remove();
                        }
                    });
                });
            }

            var extraction = extractFromBrewRendererFrame() || extractFromOuterDocument();

            if (extraction && extraction.frame) {
                return installLiveFrameReader(extraction.frame);
            }

            if (!extraction || !extraction.clone || !hasRenderedContent(extraction.clone)) {
                return 'missing-content';
            }

            copyStylesFrom(extraction.sourceDocument);

            var viewport = document.querySelector('meta[name="viewport"]');
            if (!viewport) {
                viewport = document.createElement('meta');
                viewport.setAttribute('name', 'viewport');
                document.head.appendChild(viewport);
            }
            viewport.setAttribute('content', 'width=device-width, initial-scale=1, viewport-fit=cover');

            var style = document.createElement('style');
            style.setAttribute('data-dnd-ruleslawyer-reader', 'true');
            style.textContent = [
                'html, body { min-height: 100%; margin: 0 !important; padding: 0 !important; background: #ece4cf !important; overflow-x: auto !important; }',
                'body { display: block !important; }',
                '.' + READER_CLASS + ' { box-sizing: border-box; width: max-content; max-width: none; margin: 0 auto; padding: 12px 0 24px; }',
                '.' + READER_CLASS + ' .pages { box-sizing: border-box; width: max-content; max-width: none; margin: 0 !important; padding: 0 !important; }',
                '.' + READER_CLASS + ' .page, .' + READER_CLASS + ' .phb { margin: 0 auto 16px !important; box-shadow: 0 2px 12px rgba(0, 0, 0, 0.28); }',
                '.' + READER_CLASS + ' a { pointer-events: none; cursor: default; }',
                '.brewRenderer, .brew-renderer, .toolBar, .toolbar, .headerNav, .navbar, .navBar, .popups { display: none !important; }',
                '@media (max-width: 900px) { .' + READER_CLASS + ' { padding-left: 0; padding-right: 0; } }'
            ].join('\n');

            document.body.innerHTML = '';
            document.body.appendChild(extraction.clone);
            document.head.appendChild(style);
            stripReaderChrome();

            Array.prototype.forEach.call(document.querySelectorAll('a'), function(link) {
                link.removeAttribute('href');
            });

            document.documentElement.classList.add(READY_CLASS);
            window.scrollTo(0, 0);

            return 'ok-' + extraction.source;
        })();
    """.trimIndent()
}
