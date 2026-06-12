# ShopBilling Manager Backend

Java Spring Boot backend for an agriculture hardware equipment shop.

The React frontend is now separate:

```text
..\..\ShopBillingManagerReact
```

## Features

- Owner and staff login
- Product master with category, purchase price, selling price, stock, unit, barcode, supplier, GST, low-stock alert
- Supplier records
- Billing with customer details, payment mode, discount, GST, paid amount, due amount
- Automatic stock minus when a bill is created
- PDF invoice download
- Purchase entry with automatic stock add
- Return/exchange entry with stock add back
- Dashboard with today's sales, dues, product count, low stock
- Reports for daily sales, monthly sales, low stock, customer dues
- Excel stock export
- Local SQLite database

## Default Login

- Owner: `owner` / `owner123`
- Staff: `staff` / `staff123`

Change these after the first login.

## Run Backend

Install Maven, then run:

```powershell
mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

The backend exposes APIs under:

```text
http://localhost:8080/api
```

The separate React frontend runs from `outputs/ShopBillingManagerReact`.

## Database

The SQLite database is created automatically at:

```text
data/shop-billing-manager.db
```

For backup, copy this file when the app is stopped. Excel stock export is available from the Reports page.

## Shop Settings

Edit `src/main/resources/application.properties`:

```properties
app.shop.name=ShopBilling Manager
app.shop.address=Agriculture Hardware Equipment Store
app.shop.gst=GST-NOT-SET
```
