-- Seed the events table with the 8 Bengaluru launch events.
--
-- These rows previously lived only in DataSeeder.java, which is @Profile("dev")
-- and therefore never ran in production — a prod deploy came up with an empty
-- table. As a versioned migration the data ships with every environment.
--
-- Each INSERT is guarded by NOT EXISTS on title: with baseline-on-migrate=true
-- an existing database may already hold these rows from the dev seeder, and
-- `events` has no unique constraint on title to rely on instead.

INSERT INTO events (title, category, description, image_url, date_time, venue_name, venue_address, organizer, price)
SELECT 'Sunburn Arena ft. Marshmello', 'Music',
       'Get ready for an electrifying night as Marshmello takes over Bengaluru with a high-octane EDM set. Expect dazzling visuals, booming bass, and an unforgettable crowd energy. Gates open early, so come grab your spot near the stage.',
       'https://images.unsplash.com/photo-1459749411175-04bf5292ceea?crop=entropy&cs=srgb&fm=jpg&q=85&w=1200',
       '2026-07-18T19:00:00', 'Embassy International Riding School', 'Sahakar Nagar, Bengaluru 560092',
       'Sunburn Festivals', '$45'
WHERE NOT EXISTS (SELECT 1 FROM events WHERE title = 'Sunburn Arena ft. Marshmello');

INSERT INTO events (title, category, description, image_url, date_time, venue_name, venue_address, organizer, price)
SELECT 'Indie Night Live: The Local Train', 'Music',
       'An intimate evening of soulful Hindi indie rock with The Local Train performing their greatest hits live. Sing along with thousands of fans under the open sky in this unplugged-meets-electric experience.',
       'https://images.unsplash.com/photo-1563841930606-67e2bce48b78?crop=entropy&cs=srgb&fm=jpg&q=85&w=1200',
       '2026-07-25T20:00:00', 'Phoenix Marketcity Amphitheatre', 'Whitefield, Bengaluru 560048',
       'Indie Sound Co.', '$28'
WHERE NOT EXISTS (SELECT 1 FROM events WHERE title = 'Indie Night Live: The Local Train');

INSERT INTO events (title, category, description, image_url, date_time, venue_name, venue_address, organizer, price)
SELECT 'Coldplay Tribute: A Sky Full of Stars', 'Music',
       'Relive the magic of Coldplay with a stunning tribute show featuring a full live band, LED wristbands for the crowd, and all the anthems from Yellow to Viva la Vida. A night of lights and nostalgia.',
       'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?crop=entropy&cs=srgb&fm=jpg&q=85&w=1200',
       '2026-08-02T19:30:00', 'Manpho Convention Centre', 'Nagawara, Bengaluru 560045',
       'Tribute Nights India', '$22'
WHERE NOT EXISTS (SELECT 1 FROM events WHERE title = 'Coldplay Tribute: A Sky Full of Stars');

INSERT INTO events (title, category, description, image_url, date_time, venue_name, venue_address, organizer, price)
SELECT 'Standup Special with Zakir Khan', 'Comedy',
       'India''s beloved storyteller Zakir Khan returns with a brand-new hour of relatable, laugh-out-loud comedy. From everyday heartbreaks to family chaos, get ready for ''Sakht Launda'' style humour at its finest.',
       'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?crop=entropy&cs=srgb&fm=jpg&q=85&w=1200',
       '2026-07-20T18:30:00', 'Good Shepherd Auditorium', 'Museum Road, Bengaluru 560025',
       'Comedy Central Live', '$18'
WHERE NOT EXISTS (SELECT 1 FROM events WHERE title = 'Standup Special with Zakir Khan');

INSERT INTO events (title, category, description, image_url, date_time, venue_name, venue_address, organizer, price)
SELECT 'Open Mic Comedy Night', 'Comedy',
       'A laid-back evening of fresh jokes, bold experiments, and rising talent. Watch the city''s funniest amateurs and a few surprise pro headliners test new material. Grab a drink and laugh the night away.',
       'https://images.unsplash.com/photo-1485579149621-3123dd979885?crop=entropy&cs=srgb&fm=jpg&q=85&w=1200',
       '2026-07-15T20:00:00', 'That Comedy Club', 'Koramangala 5th Block, Bengaluru 560095',
       'That Comedy Club', 'Free'
WHERE NOT EXISTS (SELECT 1 FROM events WHERE title = 'Open Mic Comedy Night');

INSERT INTO events (title, category, description, image_url, date_time, venue_name, venue_address, organizer, price)
SELECT 'Bengaluru Tech Founders Meetup', 'Meetup',
       'Connect with founders, engineers, and investors shaping Bengaluru''s startup scene. Lightning talks on AI, fintech, and scaling, followed by open networking. Bring your business cards and big ideas.',
       'https://images.unsplash.com/photo-1563461661026-49631dd5d68e?crop=entropy&cs=srgb&fm=jpg&q=85&w=1200',
       '2026-07-22T17:30:00', 'WeWork Galaxy', 'Residency Road, Bengaluru 560025',
       'Startup Grind Bengaluru', 'Free'
WHERE NOT EXISTS (SELECT 1 FROM events WHERE title = 'Bengaluru Tech Founders Meetup');

INSERT INTO events (title, category, description, image_url, date_time, venue_name, venue_address, organizer, price)
SELECT 'Product & Design Networking Mixer', 'Meetup',
       'An evening dedicated to product managers and designers. Swap portfolios, discuss craft, and meet potential collaborators over coffee and snacks. Curated intros help you meet exactly the right people.',
       'https://images.unsplash.com/photo-1675716921224-e087a0cca69a?crop=entropy&cs=srgb&fm=jpg&q=85&w=1200',
       '2026-07-29T18:00:00', '91springboard', 'Koramangala 1st Block, Bengaluru 560034',
       'Design Circle BLR', '$5'
WHERE NOT EXISTS (SELECT 1 FROM events WHERE title = 'Product & Design Networking Mixer');

INSERT INTO events (title, category, description, image_url, date_time, venue_name, venue_address, organizer, price)
SELECT 'Bengaluru Street Food Festival', 'Gathering',
       'A weekend celebration of flavours from across India and beyond. Over 60 stalls, live folk music, craft markets, and family-friendly activities. Come hungry and leave happy at the city''s biggest food carnival.',
       'https://images.unsplash.com/photo-1505426101273-b50e6f3ff9a7?crop=entropy&cs=srgb&fm=jpg&q=85&w=1200',
       '2026-08-09T12:00:00', 'Cubbon Park Grounds', 'Kasturba Road, Bengaluru 560001',
       'Namma Food Collective', '$10'
WHERE NOT EXISTS (SELECT 1 FROM events WHERE title = 'Bengaluru Street Food Festival');
