#!/usr/bin/env bash
# gen-doc.sh <chars> : writes synthetic Markdown of exactly N chars to stdout.
set -eu
n="${1:?usage: gen-doc.sh <chars>}"
block='# Heading one

Some *emphasis*, **strong text**, `inline code` and a [link](https://example.com) in a paragraph that is long enough to wrap.

## Heading two

- list item one
- list item two with **bold**
> a quoted line of text

```
fenced code block
```

'
# Repeat the block (yes adds a newline per repetition), cut to exactly N bytes (the block is pure ASCII).
yes "$block" | head -c "$n"
