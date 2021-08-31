from typing import Tuple
from requests import Session
from requests.models import Response
import json

ENV = "docker"
if ENV == "local":
    # If you are running the services on the same machine, you need to assign different ports
    USERS_API_URL = 'http://localhost:8082/api/users-service'
    ORDERS_API_URL = 'http://localhost:8083/api/orders-service'
    SHIPPING_API_URL = 'http://localhost:8084/api/shipping-service'
elif ENV == "docker":
    # In Docker we use a gateway
    USERS_API_URL = 'http://localhost/api/users-service'
    ORDERS_API_URL = 'http://localhost/api/orders-service'
    SHIPPING_API_URL = 'http://localhost/api/shipping-service'
else:
    raise ValueError("ENV unrecognized")

# -----------------------------------
#  USERS
# -----------------------------------


def register_user(s: Session, id: str, name: str, email: str, address: str, role: str) -> Tuple[Response, dict]:
    user = {
        'id': id,
        'name': name,
        'address': address,
        'email': email,
        'role': role
    }
    res = s.post(f'{USERS_API_URL}/users', json=user)

    res.raise_for_status()
    return (res, res.json())


def view_user(s: Session, user_id: str) -> Tuple[Response, dict]:
    res = s.get(f'{ORDERS_API_URL}/users/{user_id}')

    res.raise_for_status()
    return (res, res.json())

def login(user_id: str) -> Session:
    user_session = Session()
    res = user_session.post(f'{USERS_API_URL}/login',
                            data={'user-id': user_id})
    res.raise_for_status()
    return user_session

# -----------------------------------
#  PRODUCTS
# -----------------------------------


def create_product(s: Session, id: str, name: str, description: str, price: float) -> Tuple[Response, dict]:
    product = {
        'id': id,
        'name': name,
        'description': description,
        'price': price,
        'available': True
    }
    res = s.post(f'{ORDERS_API_URL}/products', json=product)

    res.raise_for_status()
    return (res, res.json())


def change_availability(s: Session, product_id: str, available: bool) -> Tuple[Response, dict]:
    res = s.patch(f'{ORDERS_API_URL}/products/{product_id}', json={'available': available})

    res.raise_for_status()
    return (res, res.json())

def view_products(s: Session) -> Tuple[Response, dict]:
    res = s.get(f'{ORDERS_API_URL}/products')

    res.raise_for_status()
    return (res, res.json())

def view_product(s: Session, product_id: str) -> Tuple[Response, dict]:
    res = s.get(f'{ORDERS_API_URL}/products/{product_id}')

    res.raise_for_status()
    return (res, res.json())


# -----------------------------------
#  ORDERS
# -----------------------------------

def create_order(s: Session, products: map) -> Tuple[Response, dict]:
    order = {
        'products': products
    }
    res = s.post(f'{ORDERS_API_URL}/orders', json=order)

    res.raise_for_status()
    return (res, res.json())

def view_orders(s: Session) -> Tuple[Response, dict]:
    res = s.get(f'{ORDERS_API_URL}/orders')

    res.raise_for_status()
    return (res, res.json())

def view_order(s: Session, order_id: str) -> Tuple[Response, dict]:
    res = s.get(f'{ORDERS_API_URL}/orders/{order_id}')

    res.raise_for_status()
    return (res, res.json())

# -----------------------------------
#  SHIPMENTS
# -----------------------------------

def view_shipments(s: Session) -> Tuple[Response, dict]:
    res = s.get(f'{SHIPPING_API_URL}/shipments')

    res.raise_for_status()
    return (res, res.json())

def view_shipment(s: Session, shipment_id: str) -> Tuple[Response, dict]:
    res = s.get(f'{SHIPPING_API_URL}/shipments/{shipment_id}')

    res.raise_for_status()
    return (res, res.json())

def notify_shipped(s: Session, shipment_id: str) -> Tuple[Response, dict]:
    res = s.post(f'{SHIPPING_API_URL}/shipments/{shipment_id}/notify-shipped')

    res.raise_for_status()
    return (res, res.json())