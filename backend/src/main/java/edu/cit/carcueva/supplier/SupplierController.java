package edu.cit.carcueva.supplier;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET  /api/supplier/orders   - every reorder we have on record (our terms)
 * POST /api/supplier/reorders - manual trigger for testing, same path as the
 *                               auto-reorder rule: { "productId": "P100", "units": 13 }
 */
@RestController
class SupplierController {

    private final SupplierGateway supplierGateway;

    SupplierController(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @GetMapping("/api/supplier/orders")
    public List<SupplierOrderView> listOrders() {
        return supplierGateway.listOrders();
    }

    @PostMapping("/api/supplier/reorders")
    public ReorderResult requestReorder(@RequestBody ManualReorderRequest request) {
        return supplierGateway.requestReorder(request.productId(), request.units());
    }
}
