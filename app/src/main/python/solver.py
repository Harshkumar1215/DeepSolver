import requests
from bs4 import BeautifulSoup
import re

def solve_mcq(question_text):
    """
    Python function to search for MCQ answers online.
    """
    try:
        # Clean the input text
        query = question_text.strip()
        if not query:
            return "No text detected."

        # Search on Google
        search_url = f"https://www.google.com/search?q={query}"
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        }

        response = requests.get(search_url, headers=headers, timeout=10)
        if response.status_code != 200:
            return "Error: Unable to reach search engine."

        soup = BeautifulSoup(response.text, 'html.parser')

        # Simple logic: Try to find common MCQ site patterns or featured snippets
        # This is a basic implementation. Real-world scraping is more complex.
        snippets = soup.find_all('div', class_='BNeawe s3v9rd AP7Wnd')
        if snippets:
            # Return the first relevant snippet text
            return snippets[0].get_text()

        return "No clear answer found. Try selecting more specific text."

    except Exception as e:
        return f"Python Error: {str(e)}"
