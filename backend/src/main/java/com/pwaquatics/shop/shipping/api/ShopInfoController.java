package com.pwaquatics.shop.shipping.api;

import com.pwaquatics.shop.shipping.application.ShippingService;
import com.pwaquatics.shop.shipping.application.view.ShopInfoView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Content")
class ShopInfoController {

    private final ShippingService shipping;

    ShopInfoController(ShippingService shipping) {
        this.shipping = shipping;
    }

    @Operation(summary = "Shop-wide notices for every page, e.g. a shipping pause for live animals")
    @GetMapping("/api/v1/shop-info")
    ShopInfoView shopInfo() {
        return shipping.shopInfo();
    }
}
