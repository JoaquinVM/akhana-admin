import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProductPhotoService } from './product-photo.service';

describe('ProductPhotoService (CAT-12)', () => {
  let service: ProductPhotoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ProductPhotoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sube el archivo con la foto esperada, si hay', () => {
    service.upload('p1', new File(['x'], 'a.jpg'), 'f1').subscribe();
    const withExpected = http.expectOne('/api/products/p1/photo');
    expect(withExpected.request.method).toBe('POST');
    expect((withExpected.request.body as FormData).get('expectedPhotoId')).toBe('f1');
    expect((withExpected.request.body as FormData).get('file')).toBeInstanceOf(File);
    withExpected.flush({ photoId: 'f2' });

    service.upload('p1', new File(['x'], 'a.jpg'), null).subscribe();
    const without = http.expectOne('/api/products/p1/photo');
    expect((without.request.body as FormData).has('expectedPhotoId')).toBe(false);
    without.flush({ photoId: 'f2' });
  });

  it('quita con la foto esperada y pide las imágenes como blob, sin extensión en la ruta', () => {
    service.remove('p1', 'f1').subscribe();
    const remove = http.expectOne('/api/products/p1/photo?expectedPhotoId=f1');
    expect(remove.request.method).toBe('DELETE');
    remove.flush(null);

    service.getImage('p1', 'f1').subscribe();
    const image = http.expectOne('/api/products/p1/photo/f1/image');
    expect(image.request.responseType).toBe('blob');
    image.flush(new Blob(['x']));

    service.getThumbnail('p1', 'f1').subscribe();
    const thumbnail = http.expectOne('/api/products/p1/photo/f1/thumbnail');
    expect(thumbnail.request.responseType).toBe('blob');
    thumbnail.flush(new Blob(['x']));
  });
});
