export function invertViewportTransform(transform) {
  if (!Array.isArray(transform) || transform.length !== 6 || !transform.every(Number.isFinite)) {
    throw new TypeError('viewport transform must contain six finite numbers');
  }
  const [a, b, c, d, e, f] = transform;
  const determinant = a * d - b * c;
  if (!Number.isFinite(determinant) || determinant === 0) throw new RangeError('viewport transform is singular');
  return [d / determinant, -b / determinant, -c / determinant, a / determinant,
    (c * f - d * e) / determinant, (b * e - a * f) / determinant].map(value => Object.is(value, -0) ? 0 : value);
}

export function rectToPdfQuad(rect, inverse) {
  if (!rect || ![rect.x, rect.y, rect.width, rect.height].every(Number.isFinite) || rect.width < 0 || rect.height < 0) {
    throw new TypeError('rectangle must have finite coordinates and nonnegative dimensions');
  }
  if (!Array.isArray(inverse) || inverse.length !== 6 || !inverse.every(Number.isFinite)) {
    throw new TypeError('inverse transform must contain six finite numbers');
  }
  const [a, b, c, d, e, f] = inverse;
  const points = [[rect.x, rect.y], [rect.x + rect.width, rect.y],
    [rect.x + rect.width, rect.y + rect.height], [rect.x, rect.y + rect.height]]
    .map(([x, y]) => [a * x + c * y + e, b * x + d * y + f]);
  if (!points.flat().every(Number.isFinite)) throw new RangeError('transformed rectangle is not finite');
  const center = [points.reduce((sum, [x]) => sum + x / 4, 0), points.reduce((sum, [, y]) => sum + y / 4, 0)];
  points.sort((left, right) => Math.atan2(left[1] - center[1], left[0] - center[0]) -
    Math.atan2(right[1] - center[1], right[0] - center[0]));
  return points.flat().map(value => Object.is(value, -0) ? 0 : value);
}

export function assertRestoredQuads(saved, observed) {
  if (!Array.isArray(saved) || !Array.isArray(observed) || !saved.length || saved.length !== observed.length) throw new Error('restored quad shape differs');
  saved.forEach((quad, i) => {
    if (!Array.isArray(quad) || !Array.isArray(observed[i]) || quad.length !== 8 || observed[i].length !== 8) throw new Error('restored quad shape differs');
    quad.forEach((point, j) => {
      if (!Number.isFinite(point) || !Number.isFinite(observed[i][j])) throw new Error('restored points must be finite');
      if (Math.abs(point - observed[i][j]) > 1) throw new Error('restored range exceeds one PDF point');
    });
  });
}
