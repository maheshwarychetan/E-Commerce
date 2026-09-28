ALTER TABLE public.product SET SCHEMA ecommerce;
ALTER TABLE public.inventory SET SCHEMA ecommerce;
ALTER TABLE public.customer SET SCHEMA ecommerce;
ALTER TABLE public.orders SET SCHEMA ecommerce;
ALTER TABLE public.order_item SET SCHEMA ecommerce;

select "current_schema"();

set search_path to ecommerce;