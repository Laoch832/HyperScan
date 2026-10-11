import { expect, it } from 'vitest';
import { paperFilter } from '../../src/image';
it('removes a paper shadow while keeping dark text and alpha', () => {
  const w = 150, h = 100, data = new Uint8ClampedArray(w * h * 4);
  for (let y=0;y<h;y++) for(let x=0;x<w;x++){const i=(y*w+x)*4;const v=(y>40&&y<48&&x>30&&x<110)?25:140+Math.round(x*90/w);data[i]=data[i+1]=data[i+2]=v;data[i+3]=255;}
  paperFilter(data,w,h,'clean');expect(data[(20*w+20)*4]).toBeGreaterThan(245);expect(data[(20*w+130)*4]).toBeGreaterThan(245);expect(data[(44*w+50)*4]).toBeLessThan(50);expect(data[(44*w+50)*4+3]).toBe(255);
});
it('creates adaptive black and white output with readable dark text', () => {
  const w=100,h=70,data=new Uint8ClampedArray(w*h*4);
  for(let i=0;i<w*h;i++){const v=(Math.floor(i/w)>30&&Math.floor(i/w)<34)?20:170;data.set([v,v,v,255],i*4);}
  paperFilter(data,w,h,'bw');expect(data[(10*w+20)*4]).toBe(255);expect(data[(32*w+20)*4]).toBe(0);
});
