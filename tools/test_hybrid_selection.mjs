import assert from 'node:assert/strict';
import { invertViewportTransform, rectToPdfQuad, assertRestoredQuads } from '../app/src/hybrid/assets/hybrid/selection-geometry.mjs';

// Actual viewport math with a non-zero CropBox origin and 90-degree rotation.
const cropAndRotatedViewport = [0, 2, 2, 0, -40, -20];
const inverse = invertViewportTransform(cropAndRotatedViewport);
assert.deepEqual(inverse, [0, 0.5, 0.5, 0, 10, 20]);
assert.deepEqual(rectToPdfQuad({ x: 20, y: 0, width: 40, height: 20 }, inverse), [10, 30, 20, 30, 20, 50, 10, 50]);
const rotated180 = [-1, 0, 0, -1, 100, 200];
assert.deepEqual(rectToPdfQuad({ x: 20, y: 30, width: 10, height: 15 }, invertViewportTransform(rotated180)), [70, 155, 80, 155, 80, 170, 70, 170]);
assert.deepEqual(rectToPdfQuad({ x: 0, y: 0, width: 10, height: 10 }, [1, 0.25, 0.5, 1, 0, 0]), [0, 0, 10, 2.5, 15, 12.5, 5, 10]);
assert.throws(() => invertViewportTransform([1, 2, 2, 4, 0, 0]), /singular/);
assert.throws(() => invertViewportTransform([1, 0, 0, 1, 0, Infinity]), /finite/);
assert.throws(() => rectToPdfQuad({ x: 0, y: 0, width: -1, height: 2 }, [1, 0, 0, 1, 0, 0]), /rectangle/);
const saved = [[10, 30, 20, 30, 20, 50, 10, 50]];
assert.doesNotThrow(() => assertRestoredQuads(saved, [[11, 30, 20, 30, 20, 50, 10, 50]]));
assert.throws(() => assertRestoredQuads(saved, [[11.001, 30, 20, 30, 20, 50, 10, 50]]), /one PDF point/);
assert.throws(() => assertRestoredQuads(saved, []), /shape/);
assert.throws(() => assertRestoredQuads(saved, [[NaN, 30, 20, 30, 20, 50, 10, 50]]), /finite/);
console.log('hybrid selection geometry tests passed');
