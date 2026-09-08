-- Add missing popular dog breeds
INSERT INTO pet_breeds (species_id, name) VALUES
((SELECT id FROM pet_species WHERE code = 'DOG'), '푸들'),
((SELECT id FROM pet_species WHERE code = 'DOG'), '요크셔테리어');
