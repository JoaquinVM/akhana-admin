import { Component, input } from '@angular/core';

export type IconName = 'close' | 'tag' | 'package' | 'chart' | 'receipt' | 'users' | 'lock' | 'unlock' | 'bag'
  | 'coins' | 'inbox' | 'check-circle' | 'handshake' | 'star' | 'cash' | 'qr' | 'leaf';

/**
 * Line icons drawn as SVG, the same style as the POS (no emojis: they look different on each phone). Sized by
 * the surrounding font size (1em) and colored with currentColor.
 */
@Component({
  selector: 'app-icon',
  standalone: true,
  templateUrl: './icon.component.html',
  styleUrls: ['./icon.component.css']
})
export class IconComponent {
  readonly name = input.required<IconName>();
}
