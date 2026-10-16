import { describe, expect, it } from 'vitest';
import { createHash } from 'node:crypto';
import { md5Hex } from '../src/md5';

describe('md5Hex', () => {
  it('matches the RFC 1321 test vectors', () => {
    expect(md5Hex('')).toBe('d41d8cd98f00b204e9800998ecf8427e');
    expect(md5Hex('a')).toBe('0cc175b9c0f1b6a831c399e269772661');
    expect(md5Hex('abc')).toBe('900150983cd24fb0d6963f7d28e17f72');
    expect(md5Hex('message digest')).toBe('f96b697d7cb7938d525a2f31aaf161d0');
    expect(md5Hex('abcdefghijklmnopqrstuvwxyz')).toBe('c3fcd3d76192e4007dfb496cca67e13b');
    expect(md5Hex('The quick brown fox jumps over the lazy dog')).toBe(
      '9e107d9d372bb6826bd81d3542a419d6',
    );
  });

  it('handles the 56/64 byte padding boundaries', () => {
    for (const length of [55, 56, 57, 63, 64, 65, 119, 120, 1000]) {
      const input = 'x'.repeat(length);
      const reference = createHash('md5').update(input, 'utf8').digest('hex');
      expect(md5Hex(input), `length ${length}`).toBe(reference);
    }
  });

  it('matches node crypto for multibyte UTF-8 input', () => {
    const input = '初音ミク / 東方Project — テスト';
    expect(md5Hex(input)).toBe(createHash('md5').update(input, 'utf8').digest('hex'));
  });
});
