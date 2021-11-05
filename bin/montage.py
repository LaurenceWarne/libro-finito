"""
Usage: python3 montage.py f

Where f is a file consisting of lines of the form
'title,image_path,bit'
e.g.
'old man's war,/home/images/oldmanswar.jpeg,0'

The bit here controls whether the image is rendered large or small
"""

import shutil, collections, os, sys, requests


DIM = (DIM_WIDTH := 128, DIM_HEIGHT := 196)
TILE_WIDTH = 6
Entry = collections.namedtuple("Entry", "title, img, large")


def proc_small_entry(path):
    name, ext = os.path.splitext(os.path.basename(path))
    os.system(
        f"convert {path} -resize {DIM_WIDTH//2}x{DIM_HEIGHT//2}\! /tmp/{name}{ext}"
    )
    return f"/tmp/{name}{ext}"


def proc_large_entry(path):
    name, ext = os.path.splitext(os.path.basename(path))
    os.system(
        f"convert {path} -resize {DIM_WIDTH}x{DIM_HEIGHT}\! /tmp/{name}"
    )
    os.system(
        f"convert /tmp/{name} -crop 2x2@ +repage +adjoin /tmp/{name}_2x2@_%d{ext}"
    )
    return [f"/tmp/{name}_2x2@_{i}{ext}" for i in range(4)]


def main():
    with open(sys.argv[1], "r") as f:
        inp = [s.split(",") for s in f.read().splitlines()]
    entries = [Entry(title, img, large == "1") for (title, img, large) in inp]
    files, current_row, nxt_row = [], [False]*TILE_WIDTH, [False]*TILE_WIDTH
    entries = entries[::-1]
    print(len(entries))
    while entries:
        entry = entries.pop()
        while all(current_row):
            files.extend(current_row)
            current_row, nxt_row = nxt_row, [False]*TILE_WIDTH
        if entry.large:
            tp_left, tp_right, btm_left, btm_right = proc_large_entry(entry.img)
            idx = current_row.index(False)
            if idx == TILE_WIDTH - 1:
                nxt_small = next(filter(lambda e: not e.large, entries[::-1]), None)
                if nxt_small:
                    entries.remove(nxt_small)
                    entries.append(entry)
                    entries.append(nxt_small)
                else:
                    current_row[TILE_WIDTH - 1] = "null:"
                    entries.append(entry)
            else:
                current_row[idx], current_row[idx + 1] = tp_left, tp_right
                nxt_row[idx], nxt_row[idx + 1] = btm_left, btm_right
        else:
            current_row[current_row.index(False)] = proc_small_entry(entry.img)
    files.extend([f or "null:" for f in current_row])
    mx_idx = max([i for i in range(TILE_WIDTH) if nxt_row[i]] + [False])
    if mx_idx:
        files.extend([f or "null:" for f in nxt_row[:mx_idx + 1]])
    print(len(files))
    os.system(
        f"montage -tile {TILE_WIDTH}x -geometry {DIM_WIDTH//2}x{DIM_HEIGHT//2}+0+0 -background transparent " +
        " ".join(files) + " montage.png"
    )
    os.system("eog montage.png")    
    

if __name__ == "__main__":
    main()
