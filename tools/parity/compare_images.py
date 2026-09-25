#!/usr/bin/env python3
"""Fail on visual drift; write a difference image without ever updating references.

Only small colour differences on existing reference edges are tolerated. Large
layout differences cannot be hidden by a whole-image percentage threshold.
"""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageFilter


def compare(reference, actual, difference):
    with Image.open(reference) as source, Image.open(actual) as candidate:
        source, candidate = source.convert('RGB'), candidate.convert('RGB')
        if source.size != candidate.size:
            raise ValueError(f'Viewport mismatch: {source.size} != {candidate.size}')
        delta = ImageChops.difference(source, candidate)
        # AA can change coverage at an existing edge, but never a flat fill.
        spread = ImageChops.difference(source.filter(ImageFilter.MaxFilter(3)), source.filter(ImageFilter.MinFilter(3)))
        edge = ImageChops.lighter(ImageChops.lighter(*spread.split()[:2]), spread.split()[2])
        magnitude = ImageChops.lighter(ImageChops.lighter(*delta.split()[:2]), delta.split()[2])
        failures = Image.new('L', source.size)
        # Separate one-channel compositor rounding from a real flat-fill drift.
        pixels = lambda image: getattr(image, 'get_flattened_data', image.getdata)()
        failures.putdata([255 if d > 1 and not (e > 0 and d <= 16) else 0
                          for d, e in zip(pixels(magnitude), pixels(edge))])
        count = failures.histogram()[255]
        difference.parent.mkdir(parents=True, exist_ok=True)
        Image.composite(Image.new('RGB', source.size, '#ff0055'), candidate, failures).save(difference)
        return count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('reference', type=Path)
    parser.add_argument('actual', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    references = sorted(args.reference.glob('*.png'))
    if not references:
        raise SystemExit('No reference screenshots')
    results = {}
    for reference in references:
        actual = args.actual / reference.name
        if not actual.exists():
            results[reference.stem] = {'error': 'Missing actual screenshot'}
            continue
        try:
            results[reference.stem] = {'differentPixels': compare(reference, actual, args.output / reference.name)}
        except ValueError as error:
            results[reference.stem] = {'error': str(error)}
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'report.json').write_text(json.dumps(results, indent=2) + '\n')
    print(json.dumps(results, indent=2))
    raise SystemExit(int(any(item.get('error') or item.get('differentPixels', 0) for item in results.values())))


if __name__ == '__main__':
    main()
