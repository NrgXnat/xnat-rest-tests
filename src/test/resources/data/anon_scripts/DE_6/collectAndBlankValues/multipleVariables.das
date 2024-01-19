version "6.6"
first_tags_to_remove := collectValues[{(0018,9020), (0044,0001)}]
second_tags_to_remove := collectValues[{(0018,9011), (0018,9100)}]
blankValues[first_tags_to_remove, second_tags_to_remove]
