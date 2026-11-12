# PW Aquatics — website gallery

What the shop looks like and how it moves, without running it. Captured on 12 November 2026
from the development build with demo data: products, customers and orders are made up, and
the animal pictures are placeholder artwork until real photos are uploaded in the admin.
Click any picture to open it at full size.

- [Video tour](#video-tour)
- [Motion](#motion)
- [Shop](#shop)
- [Cart and checkout](#cart-and-checkout)
- [Customer account](#customer-account)
- [Gallery, about and contact](#gallery-about-and-contact)
- [On a phone](#on-a-phone)
- [Admin](#admin)

## Video tour

A 26-second walk through the shop: home page, navigation, shop categories, a product with
pack sizes, add to cart and the gallery.

[![Video tour of the shop — click to play](video/tour-poster.jpg)](video/tour.mp4)

[Play the video tour (MP4, 1 MB)](video/tour.mp4)

## Motion

Every animation explains a change: where something went, what was selected, what is new.
Nothing bounces, and visitors who ask their system for less motion get instant changes
instead. The rules are in [AGENTS.md §8](../../AGENTS.md#8-frontend-conventions) and
[ADR-011](../adr/011-motion-library.md).

**Headlines rise line by line from behind a mask.** The featured animal is revealed with a
wipe.

![Home page headline reveal](motion/home-headline.gif)

**Selection markers travel instead of blinking.** The category bar and the menu underline
glide to the new choice, stretching slightly on the way. Product cards make room and arrive
30 ms apart.

![Category and menu markers](motion/selection-markers.gif)

**Add to cart.** The pack-size frame glides and the price slides over the old one. On adding,
the icon turns into a check and the button pulses once. A dot then flies in an arc to the
cart icon, and the drawer opens when it lands.

![Choosing a pack size and adding to the cart](motion/add-to-cart.gif)

**Cart.** Numbers slide in the direction they change, and a removed animal folds away while
the rest close the gap.

![Changing quantities and removing a line in the cart](motion/cart.gif)

**Gallery.** A photo grows out of its thumbnail and shrinks back into it when closed.
Switching photos crossfades in place.

![Opening, switching and closing a photo](motion/lightbox.gif)

**On a phone.** The menu slides in from the edge where its button is, with links arriving one
after another. It follows a finger and closes with a swipe.

<img src="motion/phone-menu.gif" alt="Phone menu opening and being swiped closed" width="300">

**Admin.** Picking another period updates every figure; the chart's metric switch and the
sidebar use the same sliding marker as the shop.

![Admin dashboard switches and sidebar](motion/admin.gif)

## Shop

<table>
  <tr>
    <td width="50%"><a href="images/home.jpg"><img src="images/home-preview.jpg" alt="Home page"></a><br><sub><b>Home</b>: what is in the tanks now and how live animals ship. <a href="images/home.jpg">Full page</a></sub></td>
    <td width="50%"><a href="images/shop.jpg"><img src="images/shop.jpg" alt="Shop with filters"></a><br><sub><b>Shop</b>: filters by category, availability, species, colour, grade and price, all kept in the address bar.</sub></td>
  </tr>
  <tr>
    <td><a href="images/product.jpg"><img src="images/product-preview.jpg" alt="Product page"></a><br><sub><b>Product</b>: pack sizes, quantity, shipping notes, description, care parameters and related animals. <a href="images/product.jpg">Full page</a></sub></td>
    <td><a href="images/species.jpg"><img src="images/species.jpg" alt="Species profile"></a><br><sub><b>Species profile</b>: care parameters and the animals of that species for sale.</sub></td>
  </tr>
</table>

## Cart and checkout

<table>
  <tr>
    <td width="50%"><a href="images/cart-drawer.jpg"><img src="images/cart-drawer.jpg" alt="Cart drawer"></a><br><sub><b>Cart drawer</b>: opens after adding an animal.</sub></td>
    <td width="50%"><a href="images/cart.jpg"><img src="images/cart.jpg" alt="Cart page"></a><br><sub><b>Cart</b>: prices, totals and limits all come from the server.</sub></td>
  </tr>
  <tr>
    <td><a href="images/checkout-1-address.jpg"><img src="images/checkout-1-address.jpg" alt="Checkout step 1: address"></a><br><sub><b>Checkout 1 · Address</b></sub></td>
    <td><a href="images/checkout-new-address.jpg"><img src="images/checkout-new-address.jpg" alt="Adding an address"></a><br><sub><b>Adding an address</b>: the dialog grows from the button that opened it.</sub></td>
  </tr>
  <tr>
    <td><a href="images/checkout-2-shipping.jpg"><img src="images/checkout-2-shipping.jpg" alt="Checkout step 2: shipping"></a><br><sub><b>Checkout 2 · Shipping</b>: earliest dispatch and expected delivery for live animals.</sub></td>
    <td><a href="images/checkout-3-review.jpg"><img src="images/checkout-3-review-preview.jpg" alt="Checkout step 3: review"></a><br><sub><b>Checkout 3 · Review and order</b>, with the required confirmations. <a href="images/checkout-3-review.jpg">Full page</a></sub></td>
  </tr>
  <tr>
    <td><a href="images/order-confirmation.jpg"><img src="images/order-confirmation-preview.jpg" alt="Order confirmation"></a><br><sub><b>Order placed</b>: bank transfer details and what happens next. <a href="images/order-confirmation.jpg">Full page</a></sub></td>
    <td></td>
  </tr>
</table>

## Customer account

<table>
  <tr>
    <td width="50%"><a href="images/account-overview.jpg"><img src="images/account-overview.jpg" alt="Account overview"></a><br><sub><b>Account</b>: address, profile and latest order at a glance.</sub></td>
    <td width="50%"><a href="images/account-order.jpg"><img src="images/account-order-preview.jpg" alt="Order detail"></a><br><sub><b>Order detail</b>: status, payment details, items and addresses; can be cancelled before payment. <a href="images/account-order.jpg">Full page</a></sub></td>
  </tr>
</table>

## Gallery, about and contact

<table>
  <tr>
    <td width="50%"><a href="images/gallery.jpg"><img src="images/gallery.jpg" alt="Gallery"></a><br><sub><b>Gallery</b></sub></td>
    <td width="50%"><a href="images/gallery-lightbox.jpg"><img src="images/gallery-lightbox.jpg" alt="Gallery photo viewer"></a><br><sub><b>Photo viewer</b>: arrow keys and Esc work too.</sub></td>
  </tr>
  <tr>
    <td><a href="images/about.jpg"><img src="images/about-preview.jpg" alt="About page"></a><br><sub><b>About</b>. <a href="images/about.jpg">Full page</a></sub></td>
    <td><a href="images/contact.jpg"><img src="images/contact.jpg" alt="Contact page"></a><br><sub><b>Contact</b> (the shop's email address is hidden in this picture).</sub></td>
  </tr>
</table>

## On a phone

<table>
  <tr>
    <td width="33%"><a href="images/phone-home.jpg"><img src="images/phone-home.jpg" alt="Home on a phone"></a><br><sub><b>Home</b></sub></td>
    <td width="33%"><a href="images/phone-menu.jpg"><img src="images/phone-menu.jpg" alt="Menu on a phone"></a><br><sub><b>Menu</b></sub></td>
    <td width="33%"><a href="images/phone-shop.jpg"><img src="images/phone-shop.jpg" alt="Shop on a phone"></a><br><sub><b>Shop</b></sub></td>
  </tr>
  <tr>
    <td><a href="images/phone-filters.jpg"><img src="images/phone-filters.jpg" alt="Shop filters on a phone"></a><br><sub><b>Filters</b>: slide in from the left, where their button is, and swipe away.</sub></td>
    <td><a href="images/phone-product.jpg"><img src="images/phone-product.jpg" alt="Product on a phone"></a><br><sub><b>Product</b>: with several photos, swipe between them.</sub></td>
    <td><a href="images/phone-cart.jpg"><img src="images/phone-cart.jpg" alt="Cart on a phone"></a><br><sub><b>Cart</b></sub></td>
  </tr>
</table>

## Admin

Only for administrators, at `/admin`.

<table>
  <tr>
    <td width="50%"><a href="images/admin-dashboard.jpg"><img src="images/admin-dashboard-preview.jpg" alt="Admin dashboard"></a><br><sub><b>Dashboard</b>: what needs attention now, key figures for a period, sales over time, best sellers, revenue by category, species and country, stock changes, latest orders and CSV export. <a href="images/admin-dashboard.jpg">Full page</a></sub></td>
    <td width="50%"><a href="images/admin-products.jpg"><img src="images/admin-products.jpg" alt="Admin product list"></a><br><sub><b>Products</b>: search, status and category filters.</sub></td>
  </tr>
  <tr>
    <td><a href="images/admin-product-editor.jpg"><img src="images/admin-product-editor-preview.jpg" alt="Admin product editor"></a><br><sub><b>Product editor</b>: details, descriptions with preview, stock display, search engines, pack sizes and photos. <a href="images/admin-product-editor.jpg">Full page</a></sub></td>
    <td><a href="images/admin-stock.jpg"><img src="images/admin-stock.jpg" alt="Admin stock overview"></a><br><sub><b>Stock</b>: low and sold-out filters, changes and history.</sub></td>
  </tr>
  <tr>
    <td><a href="images/admin-species.jpg"><img src="images/admin-species.jpg" alt="Admin species list"></a><br><sub><b>Species</b>: care data and trade restrictions.</sub></td>
    <td></td>
  </tr>
</table>
