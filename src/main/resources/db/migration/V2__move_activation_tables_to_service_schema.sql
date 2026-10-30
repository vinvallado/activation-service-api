CREATE SCHEMA IF NOT EXISTS activation_service;
ALTER TABLE public.activation_requests SET SCHEMA activation_service;
ALTER TABLE public.activation_tasks SET SCHEMA activation_service;
